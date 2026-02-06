/*
 * Copyright 2019-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.presenter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.framework.CastContext
import app.coradio.R
import app.coradio.shared.broadcast.AppLocalBroadcast
import app.coradio.shared.broadcast.AppLocalReceiver
import app.coradio.shared.broadcast.AppLocalReceiverCallback
import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.model.ServiceCommander
import app.coradio.shared.model.cast.CastLayer
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.media.MediaItemsSubscription
import app.coradio.shared.model.media.MediaResourceManagerListener
import app.coradio.shared.model.media.MediaResourcesManager
import app.coradio.shared.model.media.PlaybackState
import app.coradio.shared.model.net.NetworkLayer
import app.coradio.shared.model.source.SourcesLayer
import app.coradio.shared.model.storage.AppPreferencesManager
import app.coradio.shared.model.storage.FavoritesStorage
import app.coradio.shared.model.storage.LocationStorage
import app.coradio.shared.model.storage.images.ImagesStore
import app.coradio.shared.model.timer.SleepTimerListener
import app.coradio.shared.model.timer.SleepTimerModel
import app.coradio.shared.permission.PermissionChecker
import app.coradio.shared.service.CoRadioService
import app.coradio.shared.service.CoRadioStore
import app.coradio.shared.service.location.LocationService
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.MediaItemHelper
import app.coradio.shared.utils.PlayerUtils
import app.coradio.shared.utils.UiUtils
import app.coradio.shared.utils.setImageBitmap
import app.coradio.shared.utils.visible
import app.coradio.shared.view.dialog.BaseDialogFragment
import app.coradio.shared.view.dialog.EditStationDialog
import app.coradio.shared.view.dialog.RSSettingsDialog
import app.coradio.shared.view.dialog.RemoveStationDialog
import app.coradio.shared.view.list.MediaItemsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Hashtable
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MediaPresenterImpl(
    private val mContext: Context,
    private val mNetworkLayer: NetworkLayer,
    private val mLocationStorage: LocationStorage,
    private val mSleepTimerModel: SleepTimerModel,
    private val mSourcesLayer: SourcesLayer,
    private val mFavoritesStorage: FavoritesStorage,
    private val mCastLayer: CastLayer
) : MediaPresenter {
    /**
     * Manager object that acts as interface between Media Resources and current Activity.
     * Provided to this reference is a listener of events provided by Media Resource Manager.
     */
    private var mMediaRsrMgr: MediaResourcesManager? = null

    private val mMdResMrgListener = MediaResourceManagerListenerImpl()

    /**
     * Stack of the media items.
     * It is used when navigating back and forth via list.
     */
    private val mMediaItemsStack = LinkedList<String>()

    /**
     * Map of the selected and clicked positions for lists of the media items.
     * Contract is - array of integer has 2 elements {selected position, clicked position}.
     */
    private val mPositions = Hashtable<String?, IntArray?>()
    private var mListLastVisiblePosition = 0

    /**
     * ID of the parent of current item (whether it is directory or Radio Station).
     */
    private var mCurrentParentId = AppUtils.EMPTY_STRING
    private var mCallback: MediaItemsSubscription? = null
    private var mActivity: FragmentActivity? = null
    private var mMainLayoutView: View? = null
    private var mListener: MediaPresenterListener? = null
    private var mListView: RecyclerView? = null
    private val mScrollListener = ScrollListener()

    /**
     * Adapter for the representing media items in the list.
     */
    private var mAdapter: MediaItemsAdapter? = null

    /**
     * Receiver for the local application;s events
     */
    private val mAppLocalBroadcastRcvr = AppLocalReceiver.instance

    private var mCurrentRadioStationView: View? = null

    /**
     * Guardian field to prevent UI operation after addToLocals instance passed.
     */
    private val mIsOnSaveInstancePassed = AtomicBoolean(false)
    private val mIsReceiversRegistered = AtomicBoolean(false)

    private val mLocationMessenger = Messenger(LocationHandler())
    private val mTimerListener = SleepTimerListenerImpl()

    // Handle download callback from the images persistent layer.
    private val mContentObserver = ContentObserverExt()

    private val mServiceCommander = ServiceCommanderImpl()

    private val mPageCounter = AtomicInteger(0)

    override fun init(
        activity: FragmentActivity, mainLayout: View,
        bundle: Bundle, listView: RecyclerView, currentRadioStationView: View,
        adapter: MediaItemsAdapter,
        mediaSubscriptionCallback: MediaItemsSubscription,
        listener: MediaPresenterListener,
        localReceiverCallback: AppLocalReceiverCallback
    ) {
        mMediaRsrMgr = MediaResourcesManager(mContext, javaClass.simpleName, mMdResMrgListener)
        registerReceivers(localReceiverCallback)
        mIsOnSaveInstancePassed.set(false)
        mCallback = mediaSubscriptionCallback
        mActivity = activity
        mMainLayoutView = mainLayout
        mListener = listener
        mListView = listView
        mAdapter = adapter
        mCurrentRadioStationView = currentRadioStationView
        mSleepTimerModel.addSleepTimerListener(mTimerListener)
        val layoutManager = LinearLayoutManager(activity)
        mListView?.layoutManager = layoutManager
        // Set adapter
        mListView?.adapter = mAdapter
        mListView?.addOnScrollListener(mScrollListener)
        mAdapter?.listener = MediaItemsAdapterListener()
        mCurrentRadioStationView?.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                mServiceCommander.sendCommand(CoRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM)
            }
        }
        if (mMediaItemsStack.isNotEmpty()) {
            val mediaId = mMediaItemsStack[mMediaItemsStack.size - 1]
            unsubscribeFromItem(mediaId)
            addMediaItemToStack(mediaId)
        }
        restoreState(bundle)
        mActivity?.applicationContext?.contentResolver?.registerContentObserver(
            ImagesStore.AUTHORITY_URI, true,
            mContentObserver
        )
    }

    private fun itemsCount(): Int {
        return mAdapter?.itemCount ?: 0
    }

    private fun clean() {
        AppLogger.d("$TAG clean")
        mActivity?.applicationContext?.contentResolver?.unregisterContentObserver(
            mContentObserver
        )
        mMediaRsrMgr?.clean()
        mMediaRsrMgr = null
        mSleepTimerModel.removeSleepTimerListener(mTimerListener)
        mCallback = null
        mActivity = null
        mMainLayoutView = null
        mListener = null
        mListView = null
        mCurrentRadioStationView = null
        mAdapter?.clear()
        mAdapter?.removeListener()
        mAdapter = null
    }

    override fun getServiceCommander(): ServiceCommander {
        return mServiceCommander
    }

    override fun getCastContext(): CastContext? {
        return mCastLayer.getCastContext()
    }

    override fun getOnSaveInstancePassed(): Boolean {
        return mIsOnSaveInstancePassed.get()
    }

    override fun handleResume() {
        mIsOnSaveInstancePassed.set(false)
        if (mActivity == null) {
            return
        }
        val defaultCountry = mLocationStorage.getCountryCode()
        LocationService.checkCountry(mActivity!!, mMainLayoutView!!, mLocationMessenger, defaultCountry)
        if (AppUtils.hasVersionS() && AppPreferencesManager.isBtAutoPlay(mContext)) {
            if (!PermissionChecker.isBluetoothConnectGranted(mContext)) {
                PermissionChecker.requestBluetoothPermission(mActivity!!, mMainLayoutView!!)
            }
        }
    }

    override fun destroy() {
        clean()
        if (!mIsOnSaveInstancePassed.get()) {
            disconnect()
        }
        // Unregister local receivers
        unregisterReceivers()
        LocationService.doCancelWork(mContext)
    }

    private fun updateNowPlayingView(isPlaying: Boolean) {
        if (isPlaying) {
            mCurrentRadioStationView?.setBackgroundColor(ContextCompat.getColor(mContext, R.color.or_color_green_dark))
        } else {
            mCurrentRadioStationView?.setBackgroundColor(ContextCompat.getColor(mContext, R.color.or_color_primary))
        }
    }

    private fun disconnect() {
        mListView?.removeOnScrollListener(mScrollListener)
    }

    private fun exitFromUi() {
        mMediaItemsStack.clear()
        mActivity?.finish()
    }

    override fun onLocationChanged(countryCode: String) {
        mLocationStorage.setCountryCode(countryCode)
        updateRootView()
    }

    override fun getCountryCode(): String {
        return mLocationStorage.getCountryCode()
    }

    /**
     * Updates root view if there was changes in collection.
     * Should be call only if current media id is [MediaId.MEDIA_ID_ROOT] or [MediaId.MEDIA_ID_BROWSE_CAR]
     * if application runs on car.
     */
    override fun updateRootView() {
        if (getOnSaveInstancePassed()) {
            AppLogger.w("$TAG can not do Location Changed after OnSaveInstanceState")
            return
        }
        if (MediaId.MEDIA_ID_ROOT != mCurrentParentId) {
            AppLogger.w(
                "$TAG can not do Location Changed for non root. " +
                        "Current is '$mCurrentParentId'"
            )
            return
        }
        unsubscribeFromItem(mCurrentParentId)
        addMediaItemToStack(mCurrentParentId)
    }

    override fun handleBackPressed(): Boolean {
        // If there is root category - close activity
        if (mMediaItemsStack.size == 1) {
            // Un-subscribe from item
            mMediaItemsStack.removeAt(0)
            // Clear stack
            mMediaItemsStack.clear()
            CoroutineScope(Dispatchers.Main).launch {
                mServiceCommander.sendCommand(CoRadioService.CMD_STOP_SERVICE)
            }
            return true
        }
        var index = mMediaItemsStack.size - 1
        if (index >= 0) {
            // Get current media item and un-subscribe.
            mMediaItemsStack.removeAt(index)
        }

        // Subscribe to the previous item.
        index = mMediaItemsStack.size - 1
        if (index >= 0) {
            val previousMediaId = mMediaItemsStack[index]
            if (previousMediaId.isNotEmpty()) {
                mListener?.showProgressBar()
                mMediaRsrMgr?.subscribe(previousMediaId, mCallback)
            }
        } else {
            return true
        }
        return false
    }

    override fun unsubscribeFromItem(mediaId: String?) {
        // Remove provided media item (and it's duplicates, if any)
        var i = 0
        while (i < mMediaItemsStack.size) {
            if (mMediaItemsStack[i] == mediaId) {
                mMediaItemsStack.removeAt(i)
                i--
            }
            i++
        }
    }

    override fun addMediaItemToStack(mediaId: String, page: Int, bundle: Bundle) {
        if (mCallback == null) {
            AppLogger.e("$TAG add media id to stack, callback null")
            return
        }
        if (mediaId.isEmpty()) {
            AppLogger.e("$TAG add empty media id to stack")
            return
        }
        if (!mMediaItemsStack.contains(mediaId)) {
            mMediaItemsStack.add(mediaId)
        }
        mListener?.showProgressBar()
        mMediaRsrMgr?.subscribe(mediaId, mCallback, page, bundle)
    }

    override fun updateDescription(descriptionView: TextView?, mediaMetadata: MediaMetadata) {
        if (descriptionView == null) {
            return
        }
        descriptionView.text = MediaItemHelper.getDisplayDescription(
            mediaMetadata, mContext.getString(R.string.media_description_default)
        )
        if (isPlaybackStateError(mContext, descriptionView.text.toString())) {
            descriptionView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.or_color_red_light))
        } else {
            descriptionView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.or_color_transparent))
        }
    }

    private fun isPlaybackStateError(context: Context, msg: String): Boolean {
        if (msg.isEmpty()) {
            return false
        }
        return (msg == context.getString(R.string.media_stream_error)
                || msg == context.getString(R.string.media_stream_http_403)
                || msg == context.getString(R.string.media_stream_http_404))
    }

    /**
     * Sets the item on the provided index as active.
     *
     * @param position Position of the item in the list.
     */
    override fun updateActiveItem(position: Int) {
        val prevPos = mAdapter?.activeItemId ?: -1
        mAdapter?.activeItemId = position
        mAdapter?.notifyItemChanged(prevPos)
        mAdapter?.notifyItemChanged(position)
    }

    override fun isFavorite(mediaId: String): Boolean {
        return false
    }

    override fun isAdapterEmpty(): Boolean {
        return mAdapter?.itemCount == 0
    }

    override fun updateListPositions(clickPosition: Int) {
        val layoutManager = mListView?.layoutManager as LinearLayoutManager? ?: return
        mListLastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
        val size = mMediaItemsStack.size
        if (size < 1) {
            return
        }
        val mediaItem = mMediaItemsStack[size - 1]
        var data = mPositions[mediaItem]
        if (data == null) {
            data = createInitPositionEntry()
            mPositions[mediaItem] = data
        }
        data[0] = mListLastVisiblePosition
        data[1] = clickPosition
    }

    override fun handleItemSettings(item: MediaItem) {
        val transaction = getFragmentTransaction(mActivity)
        if (transaction == null) {
            AppLogger.w("$TAG can not handle settings with invalid transaction")
            return
        }
        UiUtils.clearDialogs(mActivity!!.supportFragmentManager, transaction)
        val bundle = Bundle()
        RSSettingsDialog.provideMediaItem(
            bundle, item, mCurrentParentId, itemsCount()
        )
        val fragment = BaseDialogFragment.newInstance(RSSettingsDialog::class.java.name, bundle)
        fragment.show(transaction, RSSettingsDialog.DIALOG_TAG)
    }

    @UnstableApi
    override fun handleItemSelected(item: MediaItem, clickPosition: Int) {
        if (mActivity == null) {
            return
        }
        if (!mNetworkLayer.checkConnectivityAndNotify(mActivity!!)) {
            return
        }

        val data = item.mediaMetadata
        val isBrowsable = data.isBrowsable ?: false
        val isPlayable = data.isPlayable ?: false
        if (isBrowsable) {
            if (data.title != null
                && data.title == mActivity!!.getString(R.string.category_empty)
            ) {
                return
            }
        }
        updateListPositions(clickPosition)

        // If it is browsable - then we navigate to the next category
        if (isBrowsable) {
            mPageCounter.set(0)
            addMediaItemToStack(item.mediaId)
        } else if (isPlayable) {
            // Else - we play an item
            mMediaRsrMgr?.playFromMediaId(item, mCurrentParentId)
        }
    }

    override fun handlePermissionsResult(permissions: Array<String>, grantResults: IntArray) {
        for (i in permissions.indices) {
            val isGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
            when (permissions[i]) {
                Manifest.permission.ACCESS_COARSE_LOCATION -> {
                    if (isGranted) {
                        LocationService.doEnqueueWork(mContext, mLocationMessenger)
                    }
                }

                Manifest.permission.BLUETOOTH_CONNECT -> {
                    AppPreferencesManager.setBtAutoPlay(mContext, isGranted)
                }
            }
        }
    }

    private fun getPositions(mediaItem: String): IntArray {
        // Restore clicked position for the Catalogue list.
        return if (mediaItem.isEmpty().not() && mPositions.containsKey(mediaItem)) {
            mPositions[mediaItem] ?: return createInitPositionEntry()
        } else createInitPositionEntry()
    }

    private fun createInitPositionEntry(): IntArray {
        return intArrayOf(0, DependencyRegistryCommon.UNKNOWN_ID)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun handleChildrenLoaded(
        parentId: String,
        children: List<MediaItem>
    ) {
        // Check whether category has changed.
        val isSameCatalogue = AppUtils.isSameCatalogue(parentId, mCurrentParentId)
        mCurrentParentId = parentId

        // No need to go on if indexed list ended with last item.
        if (PlayerUtils.isEndOfList(children)) {
            return
        }
        mAdapter?.parentId = parentId
        if (isSameCatalogue.not()) {
            mAdapter?.clearData()
        }
        mAdapter?.addAll(children)
        mAdapter?.notifyDataSetChanged()
        restoreSelectedPosition(parentId)
    }

    private fun restoreSelectedPosition(parentId: String) {
        // Restore positions for the Catalogue list.
        val positions = getPositions(parentId)
        val selectedPosition = positions[0]
        val clickedPosition = positions[1]
        // This will make selected item highlighted.
        updateActiveItem(clickedPosition)
        // This will do scroll to the position.
        mListView?.scrollToPosition(selectedPosition.coerceAtLeast(0))
    }

    override fun handleSaveInstanceState(outState: Bundle) {
        // Track OnSaveInstanceState passed
        mIsOnSaveInstancePassed.set(true)
        CoRadioStore.putRestoreState(outState, true)
        CoRadioStore.putCurrentParentId(outState, mCurrentParentId)
    }

    override fun handleCurrentIndexOnQueueChanged(index: Int) {
        val activePos = mAdapter?.activeItemId ?: -1
        val pos = if (activePos != -1) index else -1
        unsubscribeFromItem(mCurrentParentId)
        addMediaItemToStack(mCurrentParentId)
        Handler(Looper.getMainLooper()).postDelayed(
            {
                updateActiveItem(pos)
                updateListPositions(pos)
            }, 100
        )
    }

    /**
     * Handles action of the Radio Station edition.
     *
     * @param view View that contain tag associated with Media item related to the Radio Station to be edited.
     */
    override fun handleEditRadioStationMenu(view: View) {
        if (getOnSaveInstancePassed()) {
            AppLogger.w("$TAG can not edit after OnSaveInstanceState")
            return
        }
        val transaction = getFragmentTransaction(mActivity)
        if (transaction == null) {
            AppLogger.w("$TAG can not edit with invalid transaction")
            return
        }
        UiUtils.clearDialogs(mActivity!!.supportFragmentManager, transaction)

        val item = view.tag as MediaItem
        val mediaId = item.mediaId
        val bundle = EditStationDialog.makeBundle(mediaId)
        // Show Edit Station Dialog
        val dialog = BaseDialogFragment.newInstance(EditStationDialog::class.java.name, bundle)
        dialog.show(transaction, EditStationDialog.DIALOG_TAG)
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return mMediaRsrMgr?.currentMediaItem
    }

    override fun getCurrentCategory(): String {
        return mMediaItemsStack.last
    }

    /**
     * Handles action of the Radio Station deletion.
     *
     * @param view View that contain tag associated with Media item related to the Radio Station to be deleted.
     */
    override fun handleRemoveRadioStationMenu(view: View) {
        if (getOnSaveInstancePassed()) {
            AppLogger.w("$TAG can not show Remove RS Dialog after OnSaveInstanceState")
            return
        }
        val transaction = getFragmentTransaction(mActivity)
        if (transaction == null) {
            AppLogger.w("$TAG can not show Remove RS Dialog with invalid transaction")
            return
        }
        UiUtils.clearDialogs(mActivity!!.supportFragmentManager, transaction)

        val item = view.tag as MediaItem
        var name = AppUtils.EMPTY_STRING
        if (item.mediaMetadata.title != null) {
            name = item.mediaMetadata.title.toString()
        }

        // Show Remove Station Dialog
        val bundle = RemoveStationDialog.makeBundle(item.mediaId, name)
        val dialog = BaseDialogFragment.newInstance(RemoveStationDialog::class.java.name, bundle)
        dialog.show(transaction, RemoveStationDialog.DIALOG_TAG)
    }

    private fun restoreState(savedInstanceState: Bundle) {
        mCurrentParentId = CoRadioStore.getCurrentParentId(savedInstanceState)
        restoreSelectedPosition(mCurrentParentId)
        handleMetadataChanged(mMediaRsrMgr?.mediaMetadata)
    }

    /**
     * Register receiver for the application's local events.
     */
    private fun registerReceivers(callback: AppLocalReceiverCallback) {
        if (mIsReceiversRegistered.get()) {
            AppLogger.w("$TAG receivers are registered")
            return
        }
        mAppLocalBroadcastRcvr.registerListener(callback)

        // Create filter and add actions
        val intentFilter = IntentFilter()
        intentFilter.addAction(AppLocalBroadcast.getActionCurrentIndexOnQueueChanged())
        // Register receiver
        LocalBroadcastManager.getInstance(mContext).registerReceiver(
            mAppLocalBroadcastRcvr,
            intentFilter
        )

        mIsReceiversRegistered.set(true)
    }

    /**
     * Unregister receiver for the application's local events.
     */
    private fun unregisterReceivers() {
        if (!mIsReceiversRegistered.get()) {
            AppLogger.w("$TAG receivers are unregistered")
            return
        }
        mAppLocalBroadcastRcvr.unregisterListener()
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(
            mAppLocalBroadcastRcvr
        )
        mIsReceiversRegistered.set(false)
    }

    private fun handleMediaResourceManagerConnected() {
        val mgr = mMediaRsrMgr
        if (mgr == null) {
            AppLogger.e("$TAG md  res mgr connected but reference is null")
            return
        }
        val size = mMediaItemsStack.size
        val mediaId = if (size == 0) mgr.root else mMediaItemsStack[size - 1]
        addMediaItemToStack(mediaId)
        // Update metadata in case of UI started on and media service was already created and stream played.
        handleMetadataChanged(mgr.mediaMetadata)
    }

    private fun handleMetadataChanged(metadata: MediaMetadata?) {
        if (mListener == null) {
            return
        }
        if (metadata?.title == null) {
            return
        }
        if (mCurrentRadioStationView?.visibility != View.VISIBLE) {
            mCurrentRadioStationView.visible()
        }
        updateNowPlayingView(mMediaRsrMgr?.isPlaying == true)
        mListener?.handleMetadataChanged(metadata)
    }

    private fun onScrolledToEnd() {
        if (MediaId.isRefreshable(mCurrentParentId, mSourcesLayer)) {
            unsubscribeFromItem(mCurrentParentId)
            // TODO: When list is completed - counter still increments on each swipe up.
            addMediaItemToStack(mCurrentParentId, mPageCounter.incrementAndGet())
        } else {
            AppLogger.w("$TAG category $mCurrentParentId is not refreshable")
        }
    }

    override fun handleClosePresenter() {
        exitFromUi()
        disconnect()
    }

    /**
     * Listener for the Media Resources related events.
     */
    private inner class MediaResourceManagerListenerImpl : MediaResourceManagerListener {

        override fun onConnected() {
            handleMediaResourceManagerConnected()
        }

        override fun onPlaybackStateChanged(state: PlaybackState) {
            updateNowPlayingView(state.isPlaying)
            this@MediaPresenterImpl.mListener?.handlePlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata) {
            handleMetadataChanged(metadata)
        }
    }

    private inner class ScrollListener : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                return
            }
            updateListPositions(mAdapter?.activeItemId ?: 0)
            if (mListLastVisiblePosition == (mAdapter?.itemCount ?: 1) - 1) {
                onScrolledToEnd()
            }
        }
    }

    private inner class MediaItemsAdapterListener : MediaItemsAdapter.Listener {

        override fun onItemSettings(item: MediaItem) {
            handleItemSettings(item)
        }

        @UnstableApi
        override fun onItemSelected(item: MediaItem, position: Int) {
            updateActiveItem(position)
            handleItemSelected(item, position)
        }
    }

    @SuppressLint("HandlerLeak")
    private inner class LocationHandler : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val msgId = msg.what
            if (msgId == LocationService.MSG_ID_ON_LOCATION) {
                val countryCode = LocationService.getCountryCode(msg)
                onLocationChanged(countryCode)
            }
        }
    }

    private inner class SleepTimerListenerImpl : SleepTimerListener {

        override fun onComplete() {
            handleClosePresenter()
        }
    }

    private inner class ContentObserverExt : ContentObserver(Handler(Looper.getMainLooper())) {

        override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
            if (uri == null) {
                return
            }
            val id = ImagesStore.getId(uri)
            val view = mListView?.findViewWithTag<ImageView>(id) ?: return
            // Re-read image from the content provider.
            mContext.contentResolver.openInputStream(uri)?.use {
                view.setImageBitmap(it.readBytes())
            }
        }
    }

    private inner class ServiceCommanderImpl : ServiceCommander {

        override suspend fun sendCommand(command: String, parameters: Bundle): Boolean {
            return mMediaRsrMgr?.sendCommand(command, parameters) ?: false
        }

        override suspend fun sendCommand(
            command: String,
            parameters: Bundle,
            resultCallback: (Int, Bundle?) -> Unit
        ): Boolean {
            return mMediaRsrMgr?.sendCommand(command, parameters, resultCallback) ?: false
        }
    }

    companion object {

        private const val TAG = "MP"

        private fun getFragmentTransaction(activity: FragmentActivity?): FragmentTransaction? {
            return activity?.supportFragmentManager?.beginTransaction()
        }
    }
}
