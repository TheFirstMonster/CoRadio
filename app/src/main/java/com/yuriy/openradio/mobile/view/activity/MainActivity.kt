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

package app.coradio.mobile.view.activity

import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.material.navigation.NavigationView
import app.coradio.mobile.R
import app.coradio.mobile.view.list.MobileMediaItemsAdapter
import app.coradio.shared.broadcast.AppLocalReceiverCallback
import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.MediaPresenterDependency
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.media.MediaItemsSubscription
import app.coradio.shared.model.media.PlaybackState
import app.coradio.shared.presenter.MediaPresenter
import app.coradio.shared.presenter.MediaPresenterListener
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.PlayerUtils
import app.coradio.shared.utils.SafeToast
import app.coradio.shared.utils.UiUtils
import app.coradio.shared.utils.findCheckBox
import app.coradio.shared.utils.findFloatingActionButton
import app.coradio.shared.utils.findImageView
import app.coradio.shared.utils.findTextView
import app.coradio.shared.utils.findToolbar
import app.coradio.shared.utils.findView
import app.coradio.shared.utils.gone
import app.coradio.shared.utils.visible
import app.coradio.shared.view.dialog.AboutDialog
import app.coradio.shared.view.dialog.AddStationDialog
import app.coradio.shared.view.dialog.BaseDialogFragment
import app.coradio.shared.view.dialog.BatteryOptimizationDialog
import app.coradio.shared.view.dialog.CloudStorageDialog
import app.coradio.shared.view.dialog.EqualizerDialog
import app.coradio.shared.view.dialog.FileStorageDialog
import app.coradio.shared.view.dialog.GeneralSettingsDialog
import app.coradio.shared.view.dialog.NetworkDialog
import app.coradio.shared.view.dialog.SearchDialog
import app.coradio.shared.view.dialog.SleepTimerDialog
import app.coradio.shared.view.dialog.SourceDialog
import app.coradio.shared.view.dialog.StreamBufferingDialog
import app.coradio.shared.view.list.MediaItemsAdapter
import java.lang.ref.WeakReference

/**
 * Created with Android Studio.
 * Author: Chernyshov Yuriy - Mobile Development
 * Date: 19.12.14
 * Time: 15:13
 *
 * Main Activity class with represents the list of the categories: All, By Genre, Favorites, etc ...
 */
class MainActivity : AppCompatActivity(), MediaPresenterDependency {

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = MainActivity::class.java.simpleName
    }

    /**
     * Progress view to indicate that data is loading.
     */
    private lateinit var mProgress: ProgressBar

    /**
     * Text View to display that data has not been loaded.
     */
    private lateinit var mNoDataView: TextView

    /**
     * Member field to keep reference to the Local broadcast receiver.
     */
    private val mLocalBroadcastReceiverCb: LocalBroadcastReceiverCallback

    private lateinit var mMediaPresenter: MediaPresenter
    private var mSavedInstanceState = Bundle()
    private var mCastContext: CastContext? = null

    init {
        mLocalBroadcastReceiverCb = LocalBroadcastReceiverCallback()
    }

    override fun configureWith(mediaPresenter: MediaPresenter) {
        mMediaPresenter = mediaPresenter
        val mediaItemsAdapter = MobileMediaItemsAdapter(applicationContext, mMediaPresenter)
        val mediaSubscriptionCb = MediaItemsSubscriptionCallback(WeakReference(this))
        val mediaPresenterImpl = MediaPresenterListenerImpl()
        mMediaPresenter.init(
            this, findView(R.id.main_layout), mSavedInstanceState, findViewById(R.id.list_view),
            findViewById(R.id.current_radio_station_view), mediaItemsAdapter,
            mediaSubscriptionCb, mediaPresenterImpl, mLocalBroadcastReceiverCb
        )
    }

    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)
        AppLogger.i("$CLASS_NAME assistant content $outContent")
    }

    override fun onProvideAssistData(data: Bundle?) {
        super.onProvideAssistData(data)
        AppLogger.i("$CLASS_NAME assistant data $data")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.d("$CLASS_NAME OnCreate:$savedInstanceState")

        if (savedInstanceState != null) {
            mSavedInstanceState = Bundle(savedInstanceState)
        }

        initUi()
        hideProgressBar()

        DependencyRegistryCommonUi.inject(this)

        mCastContext = mMediaPresenter.getCastContext()

        BatteryOptimizationDialog.handle(applicationContext, supportFragmentManager)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        // Set up a MediaRouteButton to allow the user to control the current media playback route.
        CastButtonFactory.setUpMediaRouteButton(applicationContext, menu, R.id.action_cast)
        return true
    }

    @SuppressLint("NonConstantResourceId")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mMediaPresenter.getOnSaveInstancePassed()) {
            return super.onOptionsItemSelected(item)
        }
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        // DialogFragment.show() will take care of adding the fragment
        // in a transaction.  We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        val transaction = supportFragmentManager.beginTransaction()
        UiUtils.clearDialogs(supportFragmentManager, transaction)
        return when (id) {
            R.id.action_search -> {
                // Show Search Dialog
                val dialog = BaseDialogFragment.newInstance(SearchDialog::class.java.name)
                dialog.show(transaction, SearchDialog.DIALOG_TAG)
                true
            }

            R.id.action_eq -> {
                // Show Equalizer Dialog
                val dialog = BaseDialogFragment.newInstance(EqualizerDialog::class.java.name)
                dialog.show(transaction, EqualizerDialog.DIALOG_TAG)
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        AppLogger.d("$CLASS_NAME OnSaveInstance:$outState")
        mMediaPresenter.handleSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        hideNoDataMessage()
        hideProgressBar()
        if (mMediaPresenter.handleBackPressed()) {
            // Perform Android's framework lifecycle.
            super.onBackPressed()
            // Indicate that the activity is finished.
            finish()
        }
    }

    /**
     * Initialize UI components.
     */
    private fun initUi() {
        // Set content.
        setContentView(R.layout.main_drawer)
        // Initialize progress.
        mProgress = findViewById(R.id.progress_bar_view)
        // Initialize No Data text view.
        mNoDataView = findTextView(R.id.no_data_view)
        val toolbar = findToolbar(R.id.toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val addBtn = findFloatingActionButton(R.id.add_station_btn)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        navigationView.setNavigationItemSelectedListener { menuItem: MenuItem ->
            val transaction = supportFragmentManager.beginTransaction()
            UiUtils.clearDialogs(supportFragmentManager, transaction)
            menuItem.isChecked = false
            // Handle navigation view item clicks here.
            when (menuItem.itemId) {
                R.id.nav_general -> {
                    // Show General Settings Dialog
                    val dialog = BaseDialogFragment.newInstance(GeneralSettingsDialog::class.java.name)
                    dialog.show(transaction, GeneralSettingsDialog.DIALOG_TAG)
                }

                R.id.nav_buffering -> {
                    // Show Stream Buffering Dialog
                    val dialog = BaseDialogFragment.newInstance(StreamBufferingDialog::class.java.name)
                    dialog.show(transaction, StreamBufferingDialog.DIALOG_TAG)
                }

                R.id.nav_sleep_timer -> {
                    // Show Sleep Timer Dialog
                    val dialog = BaseDialogFragment.newInstance(SleepTimerDialog::class.java.name)
                    dialog.show(transaction, SleepTimerDialog.DIALOG_TAG)
                }

                R.id.nav_cloud_storage -> {
                    // Show Cloud Storage Dialog
                    val dialog = BaseDialogFragment.newInstance(CloudStorageDialog::class.java.name)
                    dialog.show(transaction, CloudStorageDialog.DIALOG_TAG)
                }

                R.id.nav_file_storage -> {
                    // Show File Storage Dialog
                    val dialog = BaseDialogFragment.newInstance(FileStorageDialog::class.java.name)
                    dialog.show(transaction, FileStorageDialog.DIALOG_TAG)
                }

                R.id.nav_about -> {
                    // Show About Dialog
                    val dialog = BaseDialogFragment.newInstance(AboutDialog::class.java.name)
                    dialog.show(transaction, AboutDialog.DIALOG_TAG)
                }

                R.id.nav_network -> {
                    // Show Network Dialog
                    val dialog = BaseDialogFragment.newInstance(NetworkDialog::class.java.name)
                    dialog.show(transaction, NetworkDialog.DIALOG_TAG)
                }

                R.id.nav_source -> {
                    // Show Source Dialog
                    val dialog = BaseDialogFragment.newInstance(SourceDialog::class.java.name)
                    dialog.show(transaction, SourceDialog.DIALOG_TAG)
                }

                else -> {
                    // No dialog found.
                }
            }
            drawer.closeDrawer(GravityCompat.START)
            true
        }

        if (DependencyRegistryCommon.isGoogleApiAvailable.not()) {
            navigationView.menu.removeItem(R.id.nav_cloud_storage)
        }

        // Handle Add Radio Station button.
        addBtn.setOnClickListener {
            // Show Add Station Dialog
            val transaction = supportFragmentManager.beginTransaction()
            val dialog = BaseDialogFragment.newInstance(AddStationDialog::class.java.name)
            dialog.show(transaction, AddStationDialog.DIALOG_TAG)
        }
    }

    /**
     * Show progress.
     */
    private fun showProgressBar() {
        if (!this::mProgress.isInitialized) {
            return
        }
        mProgress.visible()
    }

    /**
     * Hide progress.
     */
    private fun hideProgressBar() {
        if (!this::mProgress.isInitialized) {
            return
        }
        mProgress.gone()
    }

    /**
     * Show "No data" text view.
     */
    private fun showNoDataMessage() {
        if (!this::mNoDataView.isInitialized) {
            return
        }
        mNoDataView.visible()
    }

    /**
     * Hide "No data" text view.
     */
    private fun hideNoDataMessage() {
        if (!this::mNoDataView.isInitialized) {
            return
        }
        mNoDataView.gone()
    }

    fun onRemoveRSClick(view: View) {
        mMediaPresenter.handleRemoveRadioStationMenu(view)
    }

    fun onEditRSClick(view: View) {
        mMediaPresenter.handleEditRadioStationMenu(view)
    }

    @MainThread
    private fun handlePlaybackStateChanged(state: PlaybackState) {
        hideProgressBar()
    }

    /**
     * Handles event of Metadata updated.
     * Updates UI related to the currently playing Radio Station.
     *
     * @param metadata Metadata related to currently playing Radio Station.
     */
    private fun handleMetadataChanged(metadata: MediaMetadata) {
        val nameView = findTextView(R.id.crs_name_view)
        nameView.text = metadata.title
        mMediaPresenter.updateDescription(
            findTextView(R.id.crs_description_view), metadata
        )
        val imgView = findImageView(R.id.crs_img_view)
        MediaItemsAdapter.updateImage(applicationContext, metadata, imgView)
        imgView.setOnClickListener {
            mMediaPresenter.getCurrentMediaItem()?.let {
                mMediaPresenter.handleItemSettings(it)
            }
        }
        val favoriteCheckView = findCheckBox(R.id.crs_favorite_check_view)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mMediaPresenter.handlePermissionsResult(permissions, grantResults)
    }

    /**
     * Callback receiver of the local application's event.
     */
    private inner class LocalBroadcastReceiverCallback : AppLocalReceiverCallback {

        override fun onCurrentIndexOnQueueChanged(index: Int) {
            mMediaPresenter.handleCurrentIndexOnQueueChanged(index)
        }
    }

    private class MediaItemsSubscriptionCallback(private val mReference: WeakReference<MainActivity>) :
        MediaItemsSubscription {

        override fun onChildrenLoaded(
            parentId: String, children: List<MediaItem>
        ) {
            AppLogger.i(
                "$CLASS_NAME children loaded:$parentId, children:${children.size}"
            )
            val reference = mReference.get()
            if (reference == null) {
                AppLogger.w("$CLASS_NAME MediaBrowserSubscriptionCallback onChildrenLoaded reference is gone")
                return
            }
            if (reference.mMediaPresenter.getOnSaveInstancePassed()) {
                AppLogger.w("$CLASS_NAME can not perform on children loaded after OnSaveInstanceState")
                return
            }
            reference.hideProgressBar()
            val addBtn = reference.findFloatingActionButton(R.id.add_station_btn)
            if (parentId == MediaId.MEDIA_ID_ROOT) {
                addBtn.visible()
            } else {
                addBtn.gone()
            }
            if (children.isEmpty() && reference.mMediaPresenter.isAdapterEmpty()) {
                reference.showNoDataMessage()
            } else {
                reference.hideNoDataMessage()
            }

            // No need to go on if indexed list ended with last item.
            if (PlayerUtils.isEndOfList(children)) {
                return
            }
            reference.mMediaPresenter.handleChildrenLoaded(parentId, children)
        }

        override fun onError(parentId: String) {
            val reference = mReference.get()
            if (reference == null) {
                AppLogger.w("$CLASS_NAME MediaBrowserSubscriptionCallback onError reference is gone")
                return
            }
            reference.hideProgressBar()
            SafeToast.showAnyThread(
                reference.applicationContext,
                reference.getString(app.coradio.shared.R.string.error_loading_media)
            )
        }
    }

    private inner class MediaPresenterListenerImpl : MediaPresenterListener {

        override fun showProgressBar() {
            this@MainActivity.showProgressBar()
        }

        override fun handleMetadataChanged(metadata: MediaMetadata) {
            this@MainActivity.handleMetadataChanged(metadata)
        }

        override fun handlePlaybackStateChanged(state: PlaybackState) {
            this@MainActivity.handlePlaybackStateChanged(state)
        }
    }
}
