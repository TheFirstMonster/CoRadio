/*
 * Copyright 2014 - 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.coradio.shared.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.media.utils.MediaConstants
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import app.coradio.R
import app.coradio.shared.broadcast.BTConnectionReceiver
import app.coradio.shared.broadcast.BecomingNoisyReceiver
import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.extentions.isEnded
import app.coradio.shared.extentions.isPlayEnabled
import app.coradio.shared.model.media.BrowseTree
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.getStreamUrlFixed
import app.coradio.shared.model.media.isInvalid
import app.coradio.shared.model.media.item.MediaItemCommand
import app.coradio.shared.model.media.item.MediaItemCommandDependencies
import app.coradio.shared.model.net.NetworkMonitorListener
import app.coradio.shared.model.net.UrlLayer
import app.coradio.shared.model.storage.AppPreferencesManager
import app.coradio.shared.model.timer.SleepTimerListener
import app.coradio.shared.service.location.Country
import app.coradio.shared.service.player.CoRadioPlayer
import app.coradio.shared.utils.AnalyticsUtils
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.IntentUtils
import app.coradio.shared.utils.MediaItemBuilder
import app.coradio.shared.utils.MediaItemHelper
import app.coradio.shared.utils.NetUtils
import app.coradio.shared.utils.SafeToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.TreeSet
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/13/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
@UnstableApi
class CoRadioService : MediaLibraryService() {

    /**
     * Player to play Radio stream.
     */
    private val mPlayer by lazy {
        CoRadioPlayer(
            applicationContext,
            PlayerListener(),
            mPresenter.getEqualizerLayer(),
            mBrowseTree,
            mPresenter.getCastLayer()
        )
    }

    /**
     * Media Session.
     */
    private lateinit var mSession: MediaLibrarySession
    private var mBrowser: MediaSession.ControllerInfo? = null
    private var mCurrentSearchQuery = AppUtils.EMPTY_STRING

    /**
     * Track selected Radio Station.
     */
    private var mActiveRS = RadioStation.INVALID_INSTANCE
    private val mUiScope: CoroutineScope
    private val mScope: CoroutineScope
    private val mCommandScope: CoroutineScope

    private val mBTConnectionReceiver = BTConnectionReceiver(

        object : BTConnectionReceiver.Listener {
            override fun onSameDeviceConnected() {
                handleBTSameDeviceConnected()
            }

            override fun onDisconnected() {
                callPause()
            }
        }
    )
    private var mNoisyAudioStreamReceiver = BecomingNoisyReceiver(

        object : BecomingNoisyReceiver.Listener {
            override fun onAudioBecomingNoisy() {
                callPause()
            }
        }
    )
    private val mNetMonitorListener = NetworkMonitorListenerImpl()

    private val mStartIds: ConcurrentLinkedQueue<Int>
    private var mCurrentParentId = AppUtils.EMPTY_STRING
    private var mIsRestoreState = false
    private lateinit var mPresenter: CoRadioServicePresenter
    private val mSleepTimerListener = SleepTimerListenerImpl()

    private val mBrowseTree: BrowseTree by lazy {
        BrowseTree()
    }

    private val mExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var mFavoriteCommands: List<CommandButton>

    /**
     * Default constructor.
     */
    init {
        TAG = "ORS[" + hashCode() + "]"
        DependencyRegistryCommon.inject(this)
        mStartIds = ConcurrentLinkedQueue()
        mUiScope = CoroutineScope(Dispatchers.Main)
        mScope = CoroutineScope(Dispatchers.IO)
        mCommandScope = CoroutineScope(Dispatchers.IO)
    }

    interface ResultListener {

        fun onResult(
            items: List<MediaItem> = ArrayList(),
            radioStations: Set<RadioStation> = TreeSet(),
            pageNumber: Int = UrlLayer.FIRST_PAGE_INDEX
        )
    }

    fun configureWith(presenter: CoRadioServicePresenter) {
        mPresenter = presenter
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mSession
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("$TAG on create")
        if (AppPreferencesManager.lastKnownRadioStationEnabled(applicationContext)) {
            setActiveRS(mPresenter.getLastRadioStation())
        }
        registerReceivers()
        mSession = with(
            MediaLibrarySession.Builder(
                this, mPlayer, ServiceCallback()
            )
        ) {
            setId(packageName)
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@CoRadioService,
                        0,
                        sessionIntent,
                        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE
                        else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            build()
        }

        mFavoriteCommands = listOf(
            CommandButton.Builder()
                .setDisplayName(getString(R.string.favorite))
                .setEnabled(true)
                .setIconResId(R.drawable.ic_favorite_off)
                .setSessionCommand(SessionCommand(CMD_FAVORITE_OFF, Bundle()))
                .build(),
            CommandButton.Builder()
                .setDisplayName(getString(R.string.favorite))
                .setEnabled(true)
                .setIconResId(R.drawable.ic_favorite_on)
                .setSessionCommand(SessionCommand(CMD_FAVORITE_ON, Bundle()))
                .build()
        )

        updateFavoriteState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("$TAG on start $intent ${IntentUtils.intentBundleToString(intent)}, id:$startId")
        mStartIds.add(startId)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i("$TAG on destroy")
        mUiScope.cancel("Cancel on destroy")
        mScope.cancel("Cancel on destroy")
        unregisterReceivers()
        mPresenter.close()
        mSession.run {
            release()
            if (player.playbackState != Player.STATE_IDLE) {
                player.release()
            }
        }
    }

    private fun maybeNotifyRootChanged() {
        // To force update Favorite menu tab.
        var mediaId = MediaId.MEDIA_ID_ROOT
        if (mCurrentParentId == MediaId.MEDIA_ID_FAVORITES_LIST && mPresenter.getAllFavorites().isNotEmpty()) {
            // To force update Favorites list.
            mediaId = MediaId.MEDIA_ID_FAVORITES_LIST
        }
        notifyChildrenChanged(mediaId)
    }

    private fun registerReceivers() {
        mBTConnectionReceiver.register(applicationContext)
        mNoisyAudioStreamReceiver.register(applicationContext)
        mPresenter.startNetworkMonitor(applicationContext, mNetMonitorListener)
        mPresenter.getSleepTimerModel().addSleepTimerListener(mSleepTimerListener)
    }

    private fun unregisterReceivers() {
        mBTConnectionReceiver.unregister(applicationContext)
        mNoisyAudioStreamReceiver.unregister(applicationContext)
        mPresenter.stopNetworkMonitor(applicationContext)
        mPresenter.getSleepTimerModel().removeSleepTimerListener(mSleepTimerListener)
    }

    private fun updateFavoriteState() {
        if (mActiveRS != RadioStation.INVALID_INSTANCE) {
            val command =
                if (mPresenter.isRadioStationFavorite(mActiveRS)) mFavoriteCommands[1] else mFavoriteCommands[0]
            mSession.setCustomLayout(listOf(command))
        }
    }

    private fun notifyChildrenChanged(mediaId: String) {
        mBrowser?.let {
            mBrowseTree.invalidate(mediaId)
            mSession.notifyChildrenChanged(it, mediaId, 250, null)
        }
    }

    /**
     * @param exception
     */
    private fun onHandledError(exception: PlaybackException) {
        AppLogger.e("$TAG player handled exception", exception)
        val throwable = exception.cause
        if (throwable is UnrecognizedInputFormatException) {
            handleUnrecognizedInputFormatException()
        }
    }

    /**
     * Handles exception related to unrecognized url. Try to parse url deeply to extract actual stream one from
     * playlist.
     */
    private fun handleUnrecognizedInputFormatException() {
        val playlistUrl = mActiveRS.getStreamUrlFixed()
        AnalyticsUtils.logMessage("UnrecognizedInputFormat:$playlistUrl")
        handleStopRequest()
        mScope.launch(Dispatchers.IO) {
            withTimeout(API_CALL_TIMEOUT_MS) {
                if (playlistUrl.isEmpty()) {
                    AppLogger.e("HandleUnrecognizedInputFormatException with empty URL")
                    return@withTimeout
                }
                val urls = NetUtils.extractUrlsFromPlaylist(applicationContext, playlistUrl)
                mUiScope.launch {
                    // Silently clear last references and try to restart:
                    handlePlayListUrlsExtracted(urls)
                }
            }
        }
    }

    @UiThread
    private fun handlePlayListUrlsExtracted(urls: Array<String>) {
        if (urls.isEmpty()) {
            handleStopRequest()
            return
        }
        if (mActiveRS.isInvalid()) {
            handleStopRequest()
            return
        }

        mPlayer.currentMediaItem?.let {
            val curIndx = mPlayer.currentMediaItemIndex
            val newItem = it.buildUpon().setUri(urls[0]).build()
            mPlayer.replaceMediaItem(curIndx, newItem)
            handlePlayRequestUiThread()
        }
    }

    /**
     * Return Radio Station object by provided Media Id.
     *
     * @param mediaId Media Id of the station.
     *
     * @return [RadioStation] or `null`.
     */
    private fun getRadioStationByMediaId(mediaId: String?): RadioStation {
        if (mediaId.isNullOrEmpty()) {
            AppLogger.e("$TAG get rs by invalid id")
            return RadioStation.INVALID_INSTANCE
        }
        return mBrowseTree.getRadioStationByMediaId(mediaId)
    }

    /**
     * Handle a request to play Radio Station.
     */
    private fun handlePlayRequest() {
        mUiScope.launch {
            handlePlayRequestUiThread()
        }
    }

    private fun handlePlayRequestUiThread() {
        if (mPresenter.isMobileNetwork() &&
            mPresenter.getUseMobile().not()
        ) {
            SafeToast.showAnyThread(
                applicationContext,
                getString(R.string.mobile_network_disabled)
            )
            return
        }
        mPlayer.prepare()
        mPlayer.play()
    }

    private fun handleClearCache(context: Context) {
        mScope.launch {
            mPresenter.clear()
            SafeToast.showAnyThread(context, getString(R.string.clear_completed))
        }
    }

    private fun handleMasterVolumeChanged(context: Context, masterVolume: Int) {
        AppPreferencesManager.setMasterVolume(context, masterVolume)
        mUiScope.launch {
            mPlayer.volume = masterVolume / 100.0f
        }
    }

    /**
     * Handle a request to pause radio stream with reason provided.
     */
    private fun handlePauseRequest() {
        mUiScope.launch { mPlayer.pause() }
    }

    private fun closeService() {
        handleStopRequest()
        stopSelfResultInt()
        // TODO: There is on start command received after stop command ... need to find out the reason.
        //       Also, when swipe the UI, progress indicator still on for the current media.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * Handle a request to stop music.
     */
    private fun handleStopRequest() {
        mUiScope.launch { mPlayer.stop() }
    }

    private fun togglePlayableItem() {
        when {
            mPlayer.isPlaying -> mPlayer.pause()
            mPlayer.isPlayEnabled -> mPlayer.play()
            mPlayer.isEnded -> mPlayer.seekTo(C.TIME_UNSET)
            else -> {
                AppLogger.e(
                    "$TAG unhandled toggle case " +
                            "playing:${mPlayer.isPlaying} " +
                            "play enabled:${mPlayer.isPlayEnabled} " +
                            "play ended:${mPlayer.isEnded} "
                )
            }
        }
    }

    private fun stopSelfResultInt() {
        AppLogger.i("$TAG stop self with size of ${mStartIds.size}")
        while (mStartIds.isEmpty().not()) {
            val id = mStartIds.poll() ?: continue
            val result = stopSelfResult(id)
            AppLogger.i("$TAG service " + (if (result) "stopped" else "not stopped") + " for $id")
        }
    }

    /**
     * Handles event when Bluetooth connected to same device within application lifetime.
     */
    private fun handleBTSameDeviceConnected() {
        val autoPlay = AppPreferencesManager.isBtAutoPlay(applicationContext)
        if (autoPlay.not()) {
            return
        }
        handlePlayRequest()
    }

    private fun callPause() {
        handlePauseRequest()
    }

    private fun callPlayFromNetworkConnected() {
        if (mPlayer.isStoppedByNetwork().not()) {
            return
        }
        handlePlayRequest()
    }

    private fun setActiveRS(value: RadioStation) {
        if (value.isInvalid()) {
            AppLogger.w("$TAG cant set invalid ars")
            return
        }
        mActiveRS = value
        AppLogger.i("$TAG set ars $mActiveRS")
    }

    @MainThread
    private fun maybeCreateInitialPlaylist() {
        val rs = mActiveRS
        if (rs == RadioStation.INVALID_INSTANCE) {
            return
        }
        val mediaItemCount = mPlayer.mediaItemCount
        if (mediaItemCount != 0) {
            return
        }
        val rsPlayable = MediaItemBuilder.buildPlayable(rs)
        val list = mutableListOf<MediaItem>()
        val radioStations = TreeSet<RadioStation>()
        // Create a NPL:
        mScope.launch {
            AppLogger.d("$TAG create init npl")
            var radios = mPresenter.getAllFavorites()
            if (radios.isEmpty().not()) {
                AppLogger.d("$TAG create init npl fill with fav")
                radioStations.addAll(radios)
            } else {
                AppLogger.d("$TAG create init npl fill with new stations")
                radios = mPresenter.getNewStations()
                radioStations.addAll(radios)
            }
            AppLogger.d("$TAG create init npl with ${radioStations.size}")
            var isActiveStationFound = false
            for (item in radioStations) {
                val playable = MediaItemBuilder.buildPlayable(item)
                if (item.id == rs.id) {
                    isActiveStationFound = true
                }
                list.add(playable)
            }
            if (isActiveStationFound.not()) {
                list.add(0, rsPlayable)
            }
        }.invokeOnCompletion {
            mUiScope.launch {
                // This is needed for Media Browser when framework will require media item by id and the view will contain
                // Browsables only:
                mBrowseTree[rs.id] = BrowseTree.BrowseData(list, radioStations)
                val pos = list.indexOf(rsPlayable)
                mPlayer.setMediaItems(list, pos, C.TIME_UNSET)
                handlePlayRequestUiThread()
            }
        }
    }


    /**
     * Listener for Exo Player events.
     */
    private inner class PlayerListener : CoRadioPlayer.Listener {

        override fun onHandledError(error: PlaybackException) {
            this@CoRadioService.onHandledError(error)
        }

        override fun onPlaybackStateChanged(mediaItem: MediaItem) {
            mScope.launch {
                mBrowseTree.getRadioStationByMediaId(mediaItem.mediaId).let {
                    if (mediaItem.mediaMetadata.isPlayable == false) {
                        return@let
                    }
                    // TODO: Investigate why metadata with valid fields has empty media id.
                    if (it == RadioStation.INVALID_INSTANCE) {
                        AppLogger.w("$TAG playable has empty media id $it")
                        return@let
                    }
                    setActiveRS(it)
                    mPresenter.setLastRadioStation(it)
                    mUiScope.launch {
                        // Update custom commands:
                        updateFavoriteState()
                    }
                }
            }
        }
    }

    private inner class NetworkMonitorListenerImpl : NetworkMonitorListener {

        override fun onConnectivityChange(isConnected: Boolean) {
            if (isConnected.not()) {
                return
            }
            if (mPresenter.isMobileNetwork() &&
                mPresenter.getUseMobile().not()
            ) {
                SafeToast.showAnyThread(
                    applicationContext,
                    getString(R.string.mobile_network_disabled)
                )
                callPause()
                return
            }
            callPlayFromNetworkConnected()
        }
    }

    private inner class SleepTimerListenerImpl : SleepTimerListener {

        override fun onComplete() {
            AppLogger.i("$TAG on sleep timer completed")
            mUiScope.launch {
                closeService()
            }
        }
    }

    private inner class ServiceCallback : MediaLibrarySession.Callback {

        private val mSessionCmdSuccess = Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        private val mSessionCmdNotSupported =
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            AppLogger.d("$TAG [$controller] Playback Resumption")
            mBrowser = controller
            val mediaItemCount = mPlayer.mediaItemCount
            if (mediaItemCount != 0) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
                )
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    mPlayer.getPlaylist(), mPlayer.currentMediaItemIndex, 0
                )
            )
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            AppLogger.d("$TAG [$controllerInfo] Media Button Event $intent")
            mBrowser = controllerInfo
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            AppLogger.d("$TAG [$browser] Subscribe to $parentId")
            mBrowser = browser
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onUnsubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String
        ): ListenableFuture<LibraryResult<Void>> {
            AppLogger.d("$TAG [$browser] Unsubscribe from $parentId")
            mBrowser = browser
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        @UnstableApi
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            AnalyticsUtils.logMessage(
                "$TAG [$browser] GetLibraryRoot for clientPkgName=${browser.packageName}, clientUid=${browser.uid}"
            )
            mBrowser = browser
            val rootExtras = Bundle().apply {
                putBoolean(
                    MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED,
                    true
                )
            }
            //mIsRestoreState = CoRadioStore.getRestoreState(rootHints)
            val libraryParams = LibraryParams.Builder().setExtras(rootExtras).build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(MediaItemBuilder.buildRootMediaItem(), libraryParams)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            AppLogger.d("$TAG [$browser] GetChildren for $parentId page $page pageSize $pageSize")
            mBrowser = browser
            return callWhenSourceReady(parentId, page, pageSize) {
                val list = mBrowseTree[parentId] ?: ImmutableList.of()
                val sublist = list.subList(it, list.size)
                AppLogger.d("$TAG GetChildren for $parentId page $page return ${sublist.size}|${list.size} from pos:$it")
                LibraryResult.ofItemList(
                    sublist,
                    LibraryParams.Builder().build()
                )
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            AppLogger.d("$TAG [$browser] GetItem for $mediaId")
            mBrowser = browser
            val item = mBrowseTree.getMediaItemByMediaId(mediaId)
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            return Futures.immediateFuture(LibraryResult.ofItem(item, LibraryParams.Builder().build()))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            AppLogger.d("$TAG [$browser] Search for '$query'")
            mBrowser = browser
            mCurrentSearchQuery = query
            return callWhenSearchReady(query) {
                mSession.notifySearchResultChanged(browser, query, it, params)
                LibraryResult.ofVoid()
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            mBrowser = browser
            // This happens when search item clicked, browser provides no clue as to the query the item belongs to.
            // Maybe redesign is needed?
            val queryStr = if (query == AppUtils.USE_CUR_SEARCH_QUERY) {
                mCurrentSearchQuery
            } else {
                mCurrentSearchQuery = query
                query
            }
            val list = mBrowseTree[queryStr] ?: mutableListOf()
            // TODO:
            //val fromIndex = max(page * pageSize, list.size - 1)
            //val toIndex = max(fromIndex + pageSize, list.size)
            AppLogger.d("$TAG [$browser] GetSearchResult for '$queryStr'")
            // This happens when Search involved from non Automotive UI (onSearch is skipped currently).
            // Maybe need to redesign?
            if (list.isEmpty()) {
                return callWhenSearchReady(queryStr) {
                    LibraryResult.ofItemList(
                        mBrowseTree[queryStr] ?: mutableListOf(),
                        LibraryParams.Builder().build()
                    )
                }
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    list,
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            AppLogger.d("$TAG AddMediaItems ${mediaItems.size}")
            if (mediaItems.size != 1) {
                return Futures.immediateFuture(
                    mediaItems.mapNotNull {
                        mBrowseTree.getMediaItemByMediaId(it.mediaId)
                    }.toMutableList()
                )
            }
            return Futures.immediateFuture(
                mBrowseTree.getMediaItemsByMediaId(mediaItems[0].mediaId)
            )
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            AppLogger.d("$TAG Connect $controller")
            if (
                session.isMediaNotificationController(controller)
                || session.isAutomotiveController(controller)
                || session.isAutoCompanionController(controller)
            ) {
                AppLogger.w("$TAG session is not notif, not automotive, not auto")
            }
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    // Add custom commands
                    .add(SessionCommand(CMD_FAVORITE_ON, Bundle()))
                    .add(SessionCommand(CMD_FAVORITE_OFF, Bundle()))
                    .add(SessionCommand(CMD_NET_CHANGED, Bundle()))
                    .add(SessionCommand(CMD_CLEAR_CACHE, Bundle()))
                    .add(SessionCommand(CMD_MASTER_VOLUME_CHANGED, Bundle()))
                    .add(SessionCommand(CMD_STOP_SERVICE, Bundle()))
                    .add(SessionCommand(CMD_TOGGLE_LAST_PLAYED_ITEM, Bundle()))
                    .add(SessionCommand(CMD_UPDATE_SORT_IDS, Bundle()))
                    .add(SessionCommand(CMD_UPDATE_TREE, Bundle()))
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            AppLogger.d("$TAG SetMediaItems ${mediaItems.size} $startIndex")

            // TODO: It is weird that selection from the list of playable result in items of size 1, unless
            //       there is additional configuration somewhere to specify to load a whole playlist.

            if (mediaItems.size != 1) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            val mediaId = mediaItems[0].mediaId
            var items = mBrowseTree.getMediaItemsByMediaId(mediaId)

            // This is a case when Google Assistant ask to play music but nothing selected.
            if (items.isEmpty()) {
                val newItems = mutableListOf<MediaItem>()
                for (item in mBrowseTree.getMediaItemsByMediaId(mCurrentParentId)) {
                    // Verify valid configuration
                    if (item.localConfiguration == null) {
                        val msg = try {
                            "${item.mediaId}-${IntentUtils.bundleToString(item.mediaMetadata.toBundle())}"
                        } catch (exception: Exception) {
                            "Can't create a message: ${exception.message}"
                        }
                        AnalyticsUtils.logEmptyLocalConfig(msg)
                        continue
                    }
                    newItems.add(item)
                }
                // Still empty list? Add something to listen.
                if (newItems.isEmpty()) {
                    newItems.add(MediaItemBuilder.buildDefaultPlayable())
                }
                items = newItems

                val radioStations = TreeSet<RadioStation>()
                // TODO: Do we need Radio Stations as well?
//                for (i in items) {
//                    radioStations.add(***)
//                }
                mBrowseTree[items[0].mediaId] = BrowseTree.BrowseData(items, radioStations)
                mPlayer.setMediaItems(items, 0, C.TIME_UNSET)
            }

            var pos = 0
            for ((idx, i) in items.withIndex()) {
                if (i.mediaId == mediaId) {
                    pos = idx
                    break
                }
            }
            return super.onSetMediaItems(mediaSession, controller, items, pos, C.TIME_UNSET)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            AppLogger.d("$TAG CustomCommand ${customCommand.customAction}")
            return when (customCommand.customAction) {
                CMD_NET_CHANGED -> {
                    if (mPresenter.isMobileNetwork() &&
                        mPresenter.getUseMobile().not()
                    ) {
                        SafeToast.showAnyThread(
                            applicationContext,
                            getString(R.string.mobile_network_disabled)
                        )
                        handlePauseRequest()
                    }
                    return mSessionCmdSuccess
                }

                CMD_FAVORITE_OFF -> {
                    // Transition station to Favorite ON
                    session.setCustomLayout(listOf(mFavoriteCommands[1]))
                    if (handleFavorite(args, true).not()) {
                        return mSessionCmdNotSupported
                    }
                    return mSessionCmdSuccess
                }

                CMD_FAVORITE_ON -> {
                    // Transition station to Favorite OFF
                    session.setCustomLayout(listOf(mFavoriteCommands[0]))
                    if (handleFavorite(args, false).not()) {
                        return mSessionCmdNotSupported
                    }
                    return mSessionCmdSuccess
                }

                CMD_STOP_SERVICE -> {
                    closeService()
                    return mSessionCmdSuccess
                }

                CMD_TOGGLE_LAST_PLAYED_ITEM -> {
                    togglePlayableItem()
                    return mSessionCmdSuccess
                }

                CMD_UPDATE_SORT_IDS -> {
                    val mediaId = args.getString(CoRadioStore.EXTRA_KEY_MEDIA_IDS)
                    val sortId = args.getInt(CoRadioStore.EXTRA_KEY_SORT_IDS, 0)
                    val categoryMediaId =
                        args.getString(CoRadioStore.EXTRA_KEY_MEDIA_ID) ?: MediaId.MEDIA_ID_ROOT
                    if (mediaId.isNullOrEmpty()) {
                        return mSessionCmdNotSupported
                    }
                    mPresenter.updateSortIds(
                        mediaId, sortId, categoryMediaId,
                    )
                    notifyChildrenChanged(categoryMediaId)
                    return mSessionCmdSuccess
                }

                CMD_CLEAR_CACHE -> {
                    handleClearCache(applicationContext)
                    return mSessionCmdSuccess
                }

                CMD_MASTER_VOLUME_CHANGED -> {
                    handleMasterVolumeChanged(
                        applicationContext,
                        args.getInt(CoRadioStore.EXTRA_KEY_MASTER_VOLUME, MASTER_VOLUME_DEFAULT)
                    )
                    return mSessionCmdSuccess
                }

                CMD_UPDATE_TREE -> {
                    notifyChildrenChanged(mCurrentParentId)
                    return mSessionCmdSuccess
                }

                else -> {
                    mSessionCmdNotSupported
                }
            }
        }

        private fun <T> callWhenSourceReady(
            parentId: String,
            page: Int,
            pageSize: Int,
            action: (position: Int) -> T
        ): ListenableFuture<T> {
            val isSameCatalogue = AppUtils.isSameCatalogue(parentId, mCurrentParentId)
            mCurrentParentId = parentId
            var position = 0
            if (page == 0
                && mBrowseTree[parentId] != null
                //  Do not cache these categories, always fetch fresh data.
                && parentId != MediaId.MEDIA_ID_FAVORITES_LIST
                && parentId != MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
            ) {
                return Futures.immediateFuture(action(position))
            }
            val id = MediaId.getId(parentId, AppUtils.EMPTY_STRING)
            val command = mPresenter.getMediaItemCommand(id)
            AppLogger.d("$TAG get source for $id by $command")
            val defaultCountryCode = mPresenter.getCountryCode()
            // If Parent Id contains Country Code - use it in the API.
            val countryCode = MediaId.getCountryCode(parentId, defaultCountryCode)
            // Use this feature object to return response of the async nature in order it issued via this method.
            val future = SettableFuture.create<T>()
            val dependencies = MediaItemCommandDependencies(
                applicationContext, mPresenter, countryCode, parentId,
                isSameCatalogue, mIsRestoreState, Bundle(), mCommandScope,
                object : ResultListener {

                    override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {

                        if (items.isEmpty()) {
                            future.set(action(mBrowseTree[parentId]?.size ?: 0))
                            return
                        }

                        mExecutorService.submit {

                            AppLogger.d("$TAG loaded [$pageNumber:${items.size}] for $parentId")
                            if (pageNumber == 0) {
                                mBrowseTree[parentId] =
                                    BrowseTree.BrowseData(ArrayList(items), radioStations.toMutableSet())
                                mUiScope.launch { maybeCreateInitialPlaylist() }
                            } else {
                                position = mBrowseTree[parentId]?.size ?: 0
                                mBrowseTree.append(
                                    parentId,
                                    BrowseTree.BrowseData(ArrayList(items), radioStations.toMutableSet())
                                )
                            }

                            future.set(action(position))
                        }
                    }
                }
            )
            if (command != null) {
                command.execute(
                    MediaItemCommand.IUpdatePlaybackState { error ->
                        AppLogger.e("$TAG update playback state error $error")
                    },
                    dependencies
                )
            } else {
                AppLogger.w("$TAG skipping unmatched parentId: $parentId")
            }
            return future
        }

        private fun <T> callWhenSearchReady(
            query: String,
            action: (size: Int) -> T
        ): ListenableFuture<T> {
            val id = MediaId.MEDIA_ID_SEARCH_FROM_SERVICE
            val command = mPresenter.getMediaItemCommand(id)
            val future = SettableFuture.create<T>()
            val dependencies = MediaItemCommandDependencies(
                applicationContext, mPresenter, Country.COUNTRY_CODE_DEFAULT, id,
                false, mIsRestoreState, AppUtils.makeSearchQueryBundle(query), mCommandScope,

                object : ResultListener {

                    override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {

                        mExecutorService.submit {

                            mBrowseTree[query] = BrowseTree.BrowseData(ArrayList(items), radioStations.toMutableSet())
                            val size = radioStations.size
                            val result = action(size)
                            future.set(result)
                        }
                    }
                }
            )
            if (command != null) {
                command.execute(
                    MediaItemCommand.IUpdatePlaybackState { error ->
                        AppLogger.e("$TAG update playback state error $error")
                    },
                    dependencies
                )
            } else {
                AppLogger.w("$TAG skipping unmatched parentId: $id")
            }
            return future
        }

        private fun handleFavorite(args: Bundle, isFavorite: Boolean): Boolean {
            var mediaId = CoRadioStore.extractMediaId(args)
            var rs = getRadioStationByMediaId(mediaId)
            // This can happen when Favorite changed from automotive UI.
            // The assumption is this event can be delivered from the now playing item only.
            if (rs.isInvalid()) {
                if (mActiveRS.id == mediaId) {
                    rs = RadioStation.makeCopyInstance(mActiveRS)
                }
                // Lastly, get the current playing station.
                if (rs.isInvalid()) {
                    rs = RadioStation.makeCopyInstance(mActiveRS)
                    mediaId = rs.id
                }
                // We failed both cases, something went wrong ...
                if (rs.isInvalid()) {
                    return false
                }
            }
            // Update Favorites Radio station: whether add it or remove it from the storage
            mPresenter.updateRadioStationFavorite(rs, isFavorite)
            val mediaItem = mBrowseTree.getMediaItemByMediaId(mediaId)
            if (mediaItem == null) {
                AppLogger.e("Can't handle fav. for $mediaId $rs")
                return false
            }
            MediaItemHelper.updateFavoriteField(mediaItem.mediaMetadata, isFavorite)
            val currentMediaItem = mPlayer.currentMediaItem
            if (currentMediaItem?.mediaId == mediaId) {
                MediaItemHelper.updateFavoriteField(currentMediaItem.mediaMetadata, isFavorite)
            }
            mPlayer.invalidateMetaData()
            maybeNotifyRootChanged()
            return true
        }
    }

    companion object {

        const val CMD_FAVORITE_ON = "app.coradio.COMMAND.FAVORITE_ON"
        const val CMD_FAVORITE_OFF = "app.coradio.COMMAND.FAVORITE_OFF"
        const val CMD_NET_CHANGED = "app.coradio.COMMAND.NET_CHANGED"
        const val CMD_STOP_SERVICE = "app.coradio.COMMAND.STOP_SERVICE"
        const val CMD_TOGGLE_LAST_PLAYED_ITEM = "app.coradio.COMMAND.TOGGLE_LAST_PLAYED_ITEM"
        const val CMD_UPDATE_SORT_IDS = "app.coradio.COMMAND.UPDATE_SORT_IDS"
        const val CMD_CLEAR_CACHE = "app.coradio.COMMAND.CLEAR_CACHE"
        const val CMD_MASTER_VOLUME_CHANGED = "app.coradio.COMMAND.MASTER_VOLUME_CHANGED"
        const val CMD_UPDATE_TREE = "app.coradio.COMMAND.UPDATE_TREE"

        private lateinit var TAG: String

        private const val API_CALL_TIMEOUT_MS = 3_000L

        const val MASTER_VOLUME_DEFAULT = 100
    }
}
