/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.service.player

import android.content.Context
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import app.coradio.R
import app.coradio.shared.model.cast.CastLayer
import app.coradio.shared.model.eq.EqualizerLayer
import app.coradio.shared.model.media.BrowseTree
import app.coradio.shared.model.storage.AppPreferencesManager
import app.coradio.shared.service.CoRadioService
import app.coradio.shared.utils.AnalyticsUtils
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.PlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min


/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 11/04/17
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * Wrapper over ExoPlayer.
 *
 * @param mContext  Application context.
 * @param mListener Listener for the wrapper's events.
 */
@UnstableApi
class CoRadioPlayer(
    private val mContext: Context,
    private val mListener: Listener,
    private val mEqualizerLayer: EqualizerLayer,
    private val mBrowseTree: BrowseTree,
    private val mCastLayer: CastLayer
) : Player {
    /**
     * Listener for the main public events.
     */
    interface Listener {

        fun onHandledError(error: PlaybackException)

        fun onPlaybackStateChanged(mediaItem: MediaItem)
    }

    /**
     * The current player will either be an ExoPlayer (for local playback)
     * or a CastPlayer (for remote playback through a Cast device).
     */
    private var mPlayer: Player

    private val mListeners = arrayListOf<Player.Listener>()

    private val mPlaylist: MutableList<MediaItem> = arrayListOf()

    /**
     * Handler for the ExoPlayer to handle events.
     */
    private val mUiScope = CoroutineScope(Dispatchers.Main)

    /**
     * Listener of the ExoPlayer components events.
     */
    private val mComponentListener = ComponentListener()

    @Volatile
    private var mStoppedByNetwork = false

    /**
     * If Cast is available, create a CastPlayer to handle communication with a Cast session.
     */
    private val mCastPlayer: CastPlayer? by lazy {
        val castCtx = mCastLayer.getCastContext()
        AppLogger.i("Init CastPlayer with $castCtx")
        if (castCtx == null) {
            return@lazy null
        }
        try {
            CastPlayer(castCtx).apply {
                setSessionAvailabilityListener(CoRadioCastSessionAvailabilityListener())
                addListener(mComponentListener)
            }
        } catch (e: Exception) {
            // We wouldn't normally catch the generic `Exception` however
            // calling `CastContext.getSharedInstance` can throw various exceptions, all of which
            // indicate that Cast is unavailable.
            // Related internal bug b/68009560.
            AppLogger.e(
                "Cast is not available on this device. " +
                        "Exception thrown when attempting to obtain CastContext", e
            )
            null
        }
    }

    private val mExoPlayer: Player by lazy {
        AppLogger.i("Init ExoPlayer")
        val trackSelector = DefaultTrackSelector(mContext)
        trackSelector.parameters = DefaultTrackSelector.Parameters.Builder(mContext).build()
        val builder = ExoPlayer.Builder(
            mContext, ExoPlayerUtils.buildRenderersFactory(mContext)
        )
        builder.setTrackSelector(trackSelector)
        builder.setMediaSourceFactory(DefaultMediaSourceFactory(ExoPlayerUtils.getDataSourceFactory(mContext)!!))
        builder.setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    AppPreferencesManager.getMinBuffer(mContext),
                    AppPreferencesManager.getMaxBuffer(mContext),
                    AppPreferencesManager.getPlayBuffer(mContext),
                    AppPreferencesManager.getPlayBufferRebuffer(mContext)
                )
                .build()
        )
        builder.setWakeMode(C.WAKE_MODE_NETWORK)
        // Do not handle this via player - it handles OFF but doesnt ON.
        //builder.setHandleAudioBecomingNoisy(true)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setFlags(0)
            .setUsage(C.USAGE_MEDIA)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .build()
        builder.setAudioAttributes(audioAttributes, true)
        val player = builder.build()
        player.addListener(mComponentListener)
        player.playWhenReady = true
        player
    }

    init {
        mPlayer = mExoPlayer
        mPlayer.volume = AppPreferencesManager.getMasterVolume(
            mContext,
            CoRadioService.MASTER_VOLUME_DEFAULT
        ).toFloat() / 100.0f
        mEqualizerLayer.init((mExoPlayer as ExoPlayer).audioSessionId)
    }

    fun getPlaylist() : List<MediaItem> {
        return ArrayList(mPlaylist)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Player interface
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        mPlayer.setAudioAttributes(audioAttributes, handleAudioFocus)
    }

    override fun setVolume(value: Float) {
        mPlayer.volume = value
    }

    override fun pause() {
        mPlayer.pause()
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        mPlayer.replaceMediaItem(index, mediaItem)
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        mPlayer.replaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun release() {
        mPlaylist.clear()
        mEqualizerLayer.deinit()
        reset()
        mPlayer.release()
        mCastPlayer?.release()
    }

    override fun play() {
        mPlayer.play()
    }

    override fun prepare() {
        mPlayer.prepare()
    }

    override fun getApplicationLooper(): Looper {
        return mPlayer.applicationLooper
    }

    override fun addListener(listener: Player.Listener) {
        mPlayer.addListener(listener)
        mListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        mPlayer.removeListener(listener)
        mListeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        AppLogger.d("$TAG setMediaItems ${mediaItems.size}")
        mPlayer.setMediaItems(mediaItems)
        mPlaylist.clear()
        mPlaylist.addAll(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        AppLogger.d("$TAG setMediaItems ${mediaItems.size} $resetPosition")
        mPlayer.setMediaItems(mediaItems, resetPosition)
        mPlaylist.clear()
        mPlaylist.addAll(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        AppLogger.d("$TAG setMediaItems ${mediaItems.size} $startIndex $startPositionMs")
        mPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        mPlaylist.clear()
        mPlaylist.addAll(mediaItems)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        AppLogger.d("$TAG setMediaItem $mediaItem")
        mPlayer.setMediaItem(mediaItem)
        mPlaylist.clear()
        mPlaylist.add(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        AppLogger.d("$TAG setMediaItem $mediaItem $startPositionMs")
        mPlayer.setMediaItem(mediaItem, startPositionMs)
        mPlaylist.clear()
        mPlaylist.add(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        AppLogger.d("$TAG setMediaItem $mediaItem $resetPosition")
        mPlayer.setMediaItem(mediaItem, resetPosition)
        mPlaylist.clear()
        mPlaylist.add(mediaItem)
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        AppLogger.d("$TAG addMediaItem $mediaItem")
        mPlayer.addMediaItem(mediaItem)
        mPlaylist.add(mediaItem)
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        AppLogger.d("$TAG addMediaItem $index $mediaItem")
        mPlayer.addMediaItem(index, mediaItem)
        mPlaylist.add(index, mediaItem)
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        AppLogger.d("$TAG addMediaItems ${mediaItems.size}")
        mPlayer.addMediaItems(mediaItems)
        mPlaylist.addAll(mediaItems)
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        AppLogger.d("$TAG addMediaItems $index ${mediaItems.size}")
        mPlayer.addMediaItems(index, mediaItems)
        mPlaylist.addAll(index, mediaItems)
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        mPlayer.moveMediaItem(currentIndex, newIndex)
        mPlaylist.add(min(newIndex, mPlaylist.size), mPlaylist.removeAt(currentIndex))
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        val removedItems: ArrayDeque<MediaItem> = ArrayDeque()
        val removedItemsLength = toIndex - fromIndex
        for (i in removedItemsLength - 1 downTo 0) {
            removedItems.addFirst(mPlaylist.removeAt(fromIndex + i))
        }
        mPlaylist.addAll(min(newIndex, mPlaylist.size), removedItems)
    }

    override fun removeMediaItem(index: Int) {
        mPlayer.removeMediaItem(index)
        mPlaylist.removeAt(index)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        mPlayer.removeMediaItems(fromIndex, toIndex)
        val removedItemsLength = toIndex - fromIndex
        for (i in removedItemsLength - 1 downTo 0) {
            mPlaylist.removeAt(fromIndex + i)
        }
    }

    override fun clearMediaItems() {
        mPlayer.clearMediaItems()
        mPlaylist.clear()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return mPlayer.isCommandAvailable(command)
    }

    override fun canAdvertiseSession(): Boolean {
        return mPlayer.canAdvertiseSession()
    }

    override fun getAvailableCommands(): Player.Commands {
        return mPlayer.availableCommands
    }

    override fun getPlaybackState(): Int {
        return mPlayer.playbackState
    }

    override fun getPlaybackSuppressionReason(): Int {
        return mPlayer.playbackSuppressionReason
    }

    override fun isPlaying(): Boolean {
        return mPlayer.isPlaying
    }

    override fun getPlayerError(): PlaybackException? {
        return mPlayer.playerError
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        mPlayer.playWhenReady = playWhenReady
    }

    override fun getPlayWhenReady(): Boolean {
        return mPlayer.playWhenReady
    }

    override fun setRepeatMode(repeatMode: Int) {
        mPlayer.repeatMode = repeatMode
    }

    override fun getRepeatMode(): Int {
        return mPlayer.repeatMode
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        mPlayer.shuffleModeEnabled = shuffleModeEnabled
    }

    override fun getShuffleModeEnabled(): Boolean {
        return mPlayer.shuffleModeEnabled
    }

    override fun isLoading(): Boolean {
        return mPlayer.isLoading
    }

    override fun seekToDefaultPosition() {
        mPlayer.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        mPlayer.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekTo(positionMs: Long) {
        mPlayer.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        mPlayer.seekTo(mediaItemIndex, positionMs)
    }

    override fun getSeekBackIncrement(): Long {
        return mPlayer.seekBackIncrement
    }

    override fun seekBack() {
        mPlayer.seekBack()
    }

    override fun getSeekForwardIncrement(): Long {
        return mPlayer.seekForwardIncrement
    }

    override fun seekForward() {
        mPlayer.seekForward()
    }

    @Deprecated("Deprecated in Java")
    override fun hasPrevious(): Boolean {
        return mPlayer.hasPrevious()
    }

    @Deprecated("Deprecated in Java")
    override fun hasPreviousWindow(): Boolean {
        return mPlayer.hasPreviousWindow()
    }

    override fun hasPreviousMediaItem(): Boolean {
        return mPlayer.hasPreviousMediaItem()
    }

    @Deprecated("Deprecated in Java")
    override fun previous() {
        mPlayer.previous()
    }

    @Deprecated("Deprecated in Java")
    override fun seekToPreviousWindow() {
        mPlayer.seekToPreviousWindow()
    }

    override fun seekToPreviousMediaItem() {
        mPlayer.seekToPreviousMediaItem()
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return mPlayer.maxSeekToPreviousPosition
    }

    override fun seekToPrevious() {
        mPlayer.seekToPrevious()
    }

    @Deprecated("Deprecated in Java")
    override fun hasNext(): Boolean {
        return mPlayer.hasNext()
    }

    @Deprecated("Deprecated in Java")
    override fun hasNextWindow(): Boolean {
        return mPlayer.hasNextWindow()
    }

    override fun hasNextMediaItem(): Boolean {
        return mPlayer.hasNextMediaItem()
    }

    @Deprecated("Deprecated in Java")
    override fun next() {
        mPlayer.next()
    }

    @Deprecated("Deprecated in Java")
    override fun seekToNextWindow() {
        mPlayer.seekToNextWindow()
    }

    override fun seekToNextMediaItem() {
        mPlayer.seekToNextMediaItem()
    }

    override fun seekToNext() {
        mPlayer.seekToNext()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        mPlayer.playbackParameters = playbackParameters
    }

    override fun setPlaybackSpeed(speed: Float) {
        mPlayer.setPlaybackSpeed(speed)
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return mPlayer.playbackParameters
    }

    override fun stop() {
        mPlayer.stop()
    }

    override fun getCurrentTracks(): Tracks {
        return mPlayer.currentTracks
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return mPlayer.trackSelectionParameters
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        mPlayer.trackSelectionParameters = parameters
    }

    override fun getMediaMetadata(): MediaMetadata {
        return mPlayer.mediaMetadata
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return mPlayer.playlistMetadata
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        mPlayer.playlistMetadata = mediaMetadata
    }

    override fun getCurrentManifest(): Any? {
        return mPlayer.currentManifest
    }

    override fun getCurrentTimeline(): Timeline {
        return mPlayer.currentTimeline
    }

    override fun getCurrentPeriodIndex(): Int {
        return mPlayer.currentPeriodIndex
    }

    @Deprecated("Deprecated in Java")
    override fun getCurrentWindowIndex(): Int {
        return mPlayer.currentWindowIndex
    }

    override fun getCurrentMediaItemIndex(): Int {
        return mPlayer.currentMediaItemIndex
    }

    @Deprecated("Deprecated in Java")
    override fun getNextWindowIndex(): Int {
        return mPlayer.nextWindowIndex
    }

    override fun getNextMediaItemIndex(): Int {
        return mPlayer.nextMediaItemIndex
    }

    @Deprecated("Deprecated in Java")
    override fun getPreviousWindowIndex(): Int {
        return mPlayer.previousWindowIndex
    }

    override fun getPreviousMediaItemIndex(): Int {
        return mPlayer.previousMediaItemIndex
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return mPlayer.currentMediaItem
    }

    override fun getMediaItemCount(): Int {
        return mPlayer.mediaItemCount
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return mPlayer.getMediaItemAt(index)
    }

    override fun getDuration(): Long {
        return mPlayer.duration
    }

    override fun getCurrentPosition(): Long {
        return mPlayer.currentPosition
    }

    override fun getBufferedPosition(): Long {
        return mPlayer.bufferedPosition
    }

    override fun getBufferedPercentage(): Int {
        return mPlayer.bufferedPercentage
    }

    override fun getTotalBufferedDuration(): Long {
        return mPlayer.totalBufferedDuration
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowDynamic(): Boolean {
        return mPlayer.isCurrentWindowDynamic
    }

    override fun isCurrentMediaItemDynamic(): Boolean {
        return mPlayer.isCurrentMediaItemDynamic
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowLive(): Boolean {
        return mPlayer.isCurrentWindowLive
    }

    override fun isCurrentMediaItemLive(): Boolean {
        return mPlayer.isCurrentMediaItemLive
    }

    override fun getCurrentLiveOffset(): Long {
        return mPlayer.currentLiveOffset
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowSeekable(): Boolean {
        return mPlayer.isCurrentWindowSeekable
    }

    override fun isCurrentMediaItemSeekable(): Boolean {
        return mPlayer.isCurrentMediaItemSeekable
    }

    override fun isPlayingAd(): Boolean {
        return mPlayer.isPlayingAd
    }

    override fun getCurrentAdGroupIndex(): Int {
        return mPlayer.currentAdGroupIndex
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return mPlayer.currentAdIndexInAdGroup
    }

    override fun getContentDuration(): Long {
        return mPlayer.contentDuration
    }

    override fun getContentPosition(): Long {
        return mPlayer.contentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return mPlayer.contentBufferedPosition
    }

    override fun getAudioAttributes(): AudioAttributes {
        return mPlayer.audioAttributes
    }

    override fun getVolume(): Float {
        return mPlayer.volume
    }

    override fun clearVideoSurface() {
        mPlayer.clearVideoSurface()
    }

    override fun clearVideoSurface(surface: Surface?) {
        mPlayer.clearVideoSurface(surface)
    }

    override fun setVideoSurface(surface: Surface?) {
        mPlayer.setVideoSurface(surface)
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        mPlayer.setVideoSurfaceHolder(surfaceHolder)
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        mPlayer.clearVideoSurfaceHolder(surfaceHolder)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        mPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        mPlayer.clearVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        mPlayer.setVideoTextureView(textureView)
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        mPlayer.clearVideoTextureView(textureView)
    }

    override fun getVideoSize(): VideoSize {
        return mPlayer.videoSize
    }

    override fun getSurfaceSize(): Size {
        return mPlayer.surfaceSize
    }

    override fun getCurrentCues(): CueGroup {
        return mPlayer.currentCues
    }

    override fun getDeviceInfo(): DeviceInfo {
        return mPlayer.deviceInfo
    }

    override fun getDeviceVolume(): Int {
        return mPlayer.deviceVolume
    }

    override fun isDeviceMuted(): Boolean {
        return mPlayer.isDeviceMuted
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        mPlayer.setDeviceVolume(volume, flags)
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        mPlayer.deviceVolume = volume
    }

    override fun increaseDeviceVolume(flags: Int) {
        mPlayer.increaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        mPlayer.increaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        mPlayer.decreaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        mPlayer.decreaseDeviceVolume()
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        mPlayer.setDeviceMuted(muted, flags)
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
        mPlayer.isDeviceMuted = muted
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    fun invalidateMetaData() {
        mComponentListener.invalidateMetaData()
    }

    /**
     * Resets the player to its uninitialized state.
     */
    fun reset() {
        AppLogger.d("$TAG reset")
        stopCurrentPlayer()
    }

    fun isStoppedByNetwork(): Boolean {
        return mStoppedByNetwork
    }

    private fun stopCurrentPlayer() {
        mPlayer.clearMediaItems()
        mPlayer.stop()
    }

    private fun switchToPlayer(player: Player) {
        AppLogger.i("$TAG prev player: $mPlayer")
        AppLogger.i("$TAG new  player: $player")

        if (mPlayer === player) {
            return
        }

        // Player state management.
        var playbackPositionMs = C.TIME_UNSET
        var currentItemIndex = C.INDEX_UNSET
        var playWhenReady = true
        var volume = 1.0F

        val previousPlayer: Player = mPlayer
        if (previousPlayer != null) {
            // Save state from the previous player.
            val playbackState = previousPlayer.playbackState
            if (playbackState != Player.STATE_ENDED) {
                playbackPositionMs = previousPlayer.currentPosition
                playWhenReady = previousPlayer.playWhenReady
                volume = previousPlayer.volume
                currentItemIndex = previousPlayer.currentMediaItemIndex
                if (currentItemIndex != currentItemIndex) {
                    playbackPositionMs = C.TIME_UNSET
                    currentItemIndex = currentItemIndex
                }
            }
            previousPlayer.stop()
            previousPlayer.clearMediaItems()
        }

        mPlayer = player

        // Media queue management.
        player.setMediaItems(mPlaylist, currentItemIndex, playbackPositionMs)
        player.playWhenReady = playWhenReady
        player.volume = volume
        player.prepare()
    }

    /**
     * Listener class for the players components events.
     */
    private inner class ComponentListener : Player.Listener {

        private var mStreamMetadata = AppUtils.EMPTY_STRING

        private val mBufferingLabel = mContext.getString(R.string.buffering_infinite)

        private val mLiveStreamLabel = mContext.getString(R.string.media_description_default)

        /**
         * Number of currently detected playback exceptions.
         */
        private val mNumOfExceptions = AtomicInteger(0)

        private val mTrackNumber = AtomicInteger(0)

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                var title = AppUtils.EMPTY_STRING
                // See https://en.wikipedia.org/wiki/ID3#ID3v2_frame_specification
                val msg = "Metadata entry:$entry"
                AppLogger.d(msg)
                when (entry) {
                    is IcyInfo -> {
                        title = entry.title ?: AppUtils.EMPTY_STRING
                    }

                    is TextInformationFrame -> {
                        when (entry.id) {
                            ExoPlayerUtils.METADATA_ID_TT2,
                            ExoPlayerUtils.METADATA_ID_TIT2 -> {
                                val values = entry.values
                                if (values.isNotEmpty()) {
                                    title = values[0]
                                }
                            }
                        }
                    }

                    else -> {
                        AnalyticsUtils.logMetadata(msg)
                    }
                }
                if (title.isEmpty()) {
                    continue
                }
                title = title.trim { it <= ' ' }
                if (title == mStreamMetadata) {
                    continue
                }
                updateStreamMetadata(title)
            }
        }

        override fun onPlaybackStateChanged(playerState: Int) {
            val mediaItem = mPlayer.currentMediaItem
            AppLogger.d(
                "$TAG playback ${PlayerUtils.playerStateToString(playerState)} for $mediaItem"
            )
            mediaItem?.let {
                mListener.onPlaybackStateChanged(it)
            }
            when (playerState) {
                Player.STATE_READY -> {
                    mNumOfExceptions.set(0)
                }

                Player.STATE_BUFFERING -> {
                    updateStreamMetadata(mBufferingLabel)
                }

                else -> {
                    //
                }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (player.isPlaying && mStreamMetadata == mBufferingLabel) {
                updateStreamMetadata(mLiveStreamLabel)
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                && events.contains(Player.EVENT_MEDIA_METADATA_CHANGED).not()
            ) {
                // CastPlayer does not support onMetaDataChange. We can trigger this here when the
                // media item changes.
                if (mPlaylist.isNotEmpty()) {
                    for (listener in mListeners) {
                        listener.onMediaMetadataChanged(
                            mPlaylist[player.currentMediaItemIndex].mediaMetadata
                        )
                    }
                }
            }
        }

        override fun onPlayerError(exception: PlaybackException) {
            AppLogger.e("$TAG onPlayerError [${mNumOfExceptions.get()}]", exception)
            if (exception.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                mStoppedByNetwork = true
                updateStreamMetadata(toDisplayString(mContext, exception))
                return
            }
            val cause = exception.cause
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                updateStreamMetadata(toDisplayString(mContext, exception))
                return
            }
            if (mNumOfExceptions.getAndIncrement() <= MAX_EXCEPTIONS_COUNT) {
                if (cause is UnrecognizedInputFormatException) {
                    mListener.onHandledError(exception)
                } else {
                    // TODO:
                    //prepareWithList(mIndex)
                }
                return
            }
            updateStreamMetadata(toDisplayString(mContext, exception))
        }

        fun invalidateMetaData() {
            updateStreamMetadata(mStreamMetadata)
        }

        private fun updateStreamMetadata(msg: String) {
            mStreamMetadata = msg
            mUiScope.launch {
                // TODO: Need to find out how to break de-synchronization between report of metadata and
                //       invocation of this code in coroutine.
                val idx = mPlayer.currentMediaItemIndex
                if (idx >= mPlaylist.size) {
                    AppLogger.e("Update metadata, idx out of bounds")
                    return@launch
                }

                val radioStation = mBrowseTree.getRadioStationByMediaId(mPlayer.currentMediaItem?.mediaId ?: "")
                val metadata = mPlaylist[idx].mediaMetadata
                    .buildUpon()
                    .setSubtitle(msg)
                    // For Automotive:
                    .setArtist(msg)
                    // For Automotive:
                    .setAlbumTitle(radioStation.country)
                    .setExtras(mediaMetadata.extras)
                    // Use this incremental trick number to trigger metadata updates
                    .setTrackNumber(mTrackNumber.getAndIncrement())
                    .build()
                for (listener in mListeners) {
                    listener.onMediaMetadataChanged(metadata)
                }

                currentMediaItem?.let {
                    val curIndx = currentMediaItemIndex
                    val newItem = it.buildUpon().setMediaMetadata(metadata).build()
                    replaceMediaItem(curIndx, newItem)
                }
            }
        }

        private fun toDisplayString(context: Context, exception: PlaybackException): String {
            if (exception.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                return context.getString(R.string.media_stream_network_failed)
            }
            var msg = context.getString(R.string.media_stream_error)
            val cause = exception.cause
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                when (cause.responseCode) {
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        msg = context.getString(R.string.media_stream_http_403)
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        msg = context.getString(R.string.media_stream_http_404)
                    }
                }
            }
            return msg
        }
    }

    private inner class CoRadioCastSessionAvailabilityListener : SessionAvailabilityListener {

        /**
         * Called when a Cast session has started and the user wishes to control playback on a
         * remote Cast receiver rather than play audio locally.
         */
        override fun onCastSessionAvailable() {
            switchToPlayer(mCastPlayer!!)
        }

        /**
         * Called when a Cast session has ended and the user wishes to control playback locally.
         */
        override fun onCastSessionUnavailable() {
            switchToPlayer(mExoPlayer)
        }
    }

    companion object {
        /**
         * String tag to use in logs.
         */
        private const val TAG = "ORP"

        /**
         *
         */
        private const val MAX_EXCEPTIONS_COUNT = 5
    }
}
