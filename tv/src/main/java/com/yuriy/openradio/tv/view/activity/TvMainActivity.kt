/*
 * Copyright 2019-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.tv.view.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.MainThread
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.coradio.shared.broadcast.AppLocalReceiverCallback
import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.MediaPresenterDependency
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.media.MediaItemsSubscription
import app.coradio.shared.model.media.PlaybackState
import app.coradio.shared.model.media.getStreamBitrate
import app.coradio.shared.model.media.isInvalid
import app.coradio.shared.presenter.MediaPresenter
import app.coradio.shared.presenter.MediaPresenterListener
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.IntentUtils
import app.coradio.shared.utils.SafeToast
import app.coradio.shared.utils.UiUtils
import app.coradio.shared.utils.findCheckBox
import app.coradio.shared.utils.findImageView
import app.coradio.shared.utils.findTextView
import app.coradio.shared.utils.findView
import app.coradio.shared.utils.gone
import app.coradio.shared.utils.visible
import app.coradio.shared.view.dialog.AddStationDialog
import app.coradio.shared.view.dialog.BaseDialogFragment
import app.coradio.shared.view.dialog.EqualizerDialog
import app.coradio.shared.view.list.MediaItemsAdapter
import app.coradio.tv.R
import app.coradio.tv.dependencies.DependencyRegistryTv
import app.coradio.tv.view.dialog.TvSettingsDialog
import app.coradio.tv.view.list.TvMediaItemsAdapter

/*
 * Main TV Activity class that loads main TV fragment.
 */
class TvMainActivity : FragmentActivity(), MediaPresenterDependency {
    /**
     * Progress Bar view to indicate that data is loading.
     */
    private lateinit var mProgress: ProgressBar

    private lateinit var mMediaPresenter: MediaPresenter
    private lateinit var mTvMainActivityPresenter: TvMainActivityPresenter
    private var mSavedInstanceState = Bundle()

    /**
     * Member field to keep reference to the Local broadcast receiver.
     */
    private val mLocalBroadcastReceiverCb = LocalBroadcastReceiverCallback()
    private lateinit var mLauncher: ActivityResultLauncher<Intent>

    fun configureWith(presenter: TvMainActivityPresenter) {
        mTvMainActivityPresenter = presenter
    }

    override fun configureWith(mediaPresenter: MediaPresenter) {
        mMediaPresenter = mediaPresenter
        val mediaItemsAdapter = TvMediaItemsAdapter(applicationContext, mMediaPresenter)
        val mediaSubscriptionCb = MediaBrowserSubscriptionCallback()
        val mediaPresenterImpl = MediaPresenterListenerImpl()
        mMediaPresenter.init(
            this, findView(R.id.tv_main_layout), mSavedInstanceState, findViewById(R.id.tv_list_view),
            findViewById(R.id.tv_current_radio_station_view), mediaItemsAdapter,
            mediaSubscriptionCb, mediaPresenterImpl, mLocalBroadcastReceiverCb
        )
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            mSavedInstanceState = Bundle(savedInstanceState)
        }
        // Someone installed TV version on a phone ... need to prevent farther actions.
        if (!DependencyRegistryCommon.isTv) {
            SafeToast.showAnyThread(applicationContext, getString(R.string.tv_on_mobile_message))
            finish()
        }

        setContentView(R.layout.tv_main)
        setUpAddBtn()
        setUpSearchBtn()
        setUpSettingsBtn()
        setUpEqualizerBtn()

        DependencyRegistryTv.inject(this)
        DependencyRegistryCommonUi.inject(this)

        mProgress = findViewById(R.id.progress_tv_view)

        mLauncher = IntentUtils.registerForActivityResultIntrl(
            this, ::onActivityResultCallback
        )
    }

    override fun onResume() {
        super.onResume()
        mMediaPresenter.handleResume()
        hideProgressBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMediaPresenter.destroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mMediaPresenter.handleSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mMediaPresenter.handlePermissionsResult(permissions, grantResults)
    }

    /**
     * Process call back from the Search Dialog.
     */
    private fun onActivityResultCallback(data: Intent?) {
        var bundle = Bundle()
        if (data != null && data.extras != null) {
            bundle = Bundle(data.extras)
        }
        mMediaPresenter.unsubscribeFromItem(MediaId.MEDIA_ID_SEARCH_FROM_APP)
        mMediaPresenter.addMediaItemToStack(MediaId.MEDIA_ID_SEARCH_FROM_APP, bundle = bundle)
    }

    /**
     * Show progress bar.
     */
    private fun showProgressBar() {
        if (this::mProgress.isInitialized) {
            mProgress.visible()
        }
    }

    /**
     * Hide progress bar.
     */
    private fun hideProgressBar() {
        if (this::mProgress.isInitialized) {
            mProgress.gone()
        }
    }

    override fun onBackPressed() {
        hideProgressBar()
        if (mMediaPresenter.handleBackPressed()) {
            // Perform Android's framework lifecycle.
            super.onBackPressed()
            // Indicate that the activity is finished.
            finish()
        }
    }

    @MainThread
    private fun handlePlaybackStateChanged(state: PlaybackState) {
        hideProgressBar()
    }

    private fun setUpSettingsBtn() {
        val button = findImageView(R.id.tv_settings_btn)
        button.setOnClickListener { showTvSettings() }
    }

    private fun setUpSearchBtn() {
        val button = findImageView(R.id.tv_search_btn)
        button.setOnClickListener {
            mLauncher.launch(TvSearchActivity.makeStartIntent(this))
        }
    }

    private fun setUpEqualizerBtn() {
        val button = findImageView(R.id.tv_eq_btn)
        button.setOnClickListener {
            // Show Equalizer Dialog
            val transaction = supportFragmentManager.beginTransaction()
            val dialog = BaseDialogFragment.newInstance(
                EqualizerDialog::class.java.name
            )
            dialog.show(transaction, EqualizerDialog.DIALOG_TAG)
        }
    }

    private fun setUpAddBtn() {
        val button = findImageView(R.id.tv_add_btn)
        button.setOnClickListener {
            // Show Add Station Dialog
            val transaction = supportFragmentManager.beginTransaction()
            val dialog = BaseDialogFragment.newInstance(
                AddStationDialog::class.java.name
            )
            dialog.show(transaction, AddStationDialog.DIALOG_TAG)
        }
    }

    private fun showTvSettings() {
        val transaction = supportFragmentManager.beginTransaction()
        UiUtils.clearDialogs(supportFragmentManager, transaction)
        // Show Settings Dialog
        val dialogFragment = BaseDialogFragment.newInstance(
            TvSettingsDialog::class.java.name
        )
        dialogFragment.show(transaction, TvSettingsDialog.DIALOG_TAG)
    }

    /**
     * Handles event of Metadata updated.
     * Updates UI related to the currently playing Radio Station.
     *
     * @param metadata Metadata related to currently playing Radio Station.
     */
    private fun handleMetadataChanged(metadata: MediaMetadata) {
        val radioStation = mTvMainActivityPresenter.getLastRadioStation()
        if (radioStation.isInvalid()) {
            // TODO: Improve this.
            return
        }
        val nameView = findTextView(R.id.tv_crs_name_view)
        nameView.text = metadata.title
        mMediaPresenter.updateDescription(
            findTextView(R.id.tv_crs_description_view), metadata
        )
        val imgView = findImageView(R.id.tv_crs_img_view)
        MediaItemsAdapter.updateImage(applicationContext, metadata, imgView)
        MediaItemsAdapter.updateBitrateView(
            radioStation.getStreamBitrate(),
            findTextView(R.id.tv_crs_bitrate_view), true
        )

        val favoriteCheckView = findCheckBox(R.id.tv_crs_favorite_check_view)
        favoriteCheckView.isChecked = false
        mMediaPresenter.getCurrentMediaItem()?.let {
            MediaItemsAdapter.handleFavoriteAction(
                favoriteCheckView,
                it.mediaId,
                metadata,
                mMediaPresenter.getServiceCommander()
            )
        }
    }

    private fun handleChildrenLoaded(
        parentId: String,
        children: List<MediaItem>
    ) {
        if (mMediaPresenter.getOnSaveInstancePassed()) {
            AppLogger.w("$CLASS_NAME can not perform on children loaded after OnSaveInstanceState")
            return
        }
        hideProgressBar()
        mMediaPresenter.handleChildrenLoaded(parentId, children)
    }

    fun onRemoveRSClick(view: View) {
        mMediaPresenter.handleRemoveRadioStationMenu(view)
    }

    fun onEditRSClick(view: View) {
        mMediaPresenter.handleEditRadioStationMenu(view)
    }

    private inner class MediaBrowserSubscriptionCallback : MediaItemsSubscription {

        override fun onChildrenLoaded(
            parentId: String,
            children: List<MediaItem>
        ) {
            handleChildrenLoaded(parentId, children)
        }

        override fun onError(parentId: String) {
            SafeToast.showAnyThread(
                applicationContext,
                getString(app.coradio.shared.R.string.error_loading_media)
            )
        }
    }

    private inner class MediaPresenterListenerImpl : MediaPresenterListener {
        override fun showProgressBar() {
            this@TvMainActivity.showProgressBar()
        }

        override fun handleMetadataChanged(metadata: MediaMetadata) {
            this@TvMainActivity.handleMetadataChanged(metadata)
        }

        override fun handlePlaybackStateChanged(state: PlaybackState) {
            this@TvMainActivity.handlePlaybackStateChanged(state)
        }
    }

    /**
     * Callback receiver of the local application's event.
     */
    private inner class LocalBroadcastReceiverCallback : AppLocalReceiverCallback {

        override fun onCurrentIndexOnQueueChanged(index: Int) {
            mMediaPresenter.handleCurrentIndexOnQueueChanged(index)
        }
    }

    companion object {
        private val CLASS_NAME = TvMainActivity::class.java.simpleName
    }
}
