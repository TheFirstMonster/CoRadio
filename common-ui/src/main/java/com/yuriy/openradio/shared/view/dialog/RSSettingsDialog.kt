/*
 * Copyright 2020-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.view.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.coradio.shared.R
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.MediaPresenterDependency
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.presenter.MediaPresenter
import app.coradio.shared.service.CoRadioService
import app.coradio.shared.service.CoRadioStore
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.MediaItemHelper
import app.coradio.shared.utils.findImageView
import app.coradio.shared.utils.findTextView
import app.coradio.shared.utils.findView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/20/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class RSSettingsDialog : BaseDialogFragment(), MediaPresenterDependency {

    private var mParentCategoryId = AppUtils.EMPTY_STRING
    private var mSortMediaId = AppUtils.EMPTY_STRING
    private var mSortNewPosition = 0
    private var mSortOriginalPosition = 0
    private lateinit var mMediaPresenter: MediaPresenter

    override fun configureWith(mediaPresenter: MediaPresenter) {
        mMediaPresenter = mediaPresenter
    }

    override fun onCreateDialog(savedInstance: Bundle?): Dialog {
        val view = inflater.inflate(
            R.layout.dialog_rs_settings,
            requireActivity().findViewById(R.id.dialog_rs_settings_root)
        )
        setWindowDimensions(view, 0.8f, 0.3f)

        DependencyRegistryCommonUi.inject(this)

        val item = extractMediaItem(arguments)
        if (item != null) {
            handleUi(view, item, arguments)
        } else {
            //TODO: Address null data
            AppLogger.e("Setting dialog provided with null data")
        }
        return createAlertDialog(view)
    }

    override fun onPause() {
        if (mSortNewPosition != mSortOriginalPosition) {
            CoroutineScope(Dispatchers.Main).launch {
                val bundle = CoRadioStore.makeUpdateSortIdsBundle(mSortMediaId, mSortNewPosition, mParentCategoryId)
                mMediaPresenter.getServiceCommander().sendCommand(CoRadioService.CMD_UPDATE_SORT_IDS, bundle)
                mMediaPresenter.handleCurrentIndexOnQueueChanged(mSortNewPosition)
            }
        }
        super.onPause()
    }

    private fun handleUi(view: View, item: MediaItem, args: Bundle?) {
        mParentCategoryId = extractParentId(args)
        mSortMediaId = item.mediaId
        val isLocal = MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST == mParentCategoryId
        val isSortable = MediaId.isSortable(mParentCategoryId)
        view.findView(R.id.dialog_rs_settings_edit_remove).visibility =
            if (isLocal) View.VISIBLE else View.GONE
        view.findView(R.id.dialog_rs_settings_sort).visibility =
            if (isSortable) View.VISIBLE else View.GONE

        view.findView(R.id.dialog_rs_settings_edit_btn).tag = item
        view.findView(R.id.dialog_rs_settings_remove_btn).tag = item
        val name = view.findTextView(R.id.dialog_rs_settings_rs_name)
        name.text = item.mediaMetadata.title

        val imageView = view.findImageView(R.id.dialog_rs_settings_logo_view)
        // At this point Image should be fetched from the internet or local file system.
        try {
            imageView.setImageURI(item.mediaMetadata.artworkUri)
        } catch (exception: NullPointerException) {
            // TODO: Try to understand:
            //       Attempt to invoke virtual method
            //       'java.io.FileDescriptor android.content.res.AssetFileDescriptor.getFileDescriptor()'
            //       on a null object reference
        }

        if (isSortable) {
            handleSortUi(view, item, args)
        }
    }

    private fun handleSortUi(view: View, item: MediaItem, args: Bundle?) {
        val maxId = extractMaxId(args)
        val numPicker = view.findViewById<NumberPicker>(R.id.sort_id_picker)
        numPicker.minValue = 0
        numPicker.maxValue = maxId
        numPicker.value = MediaItemHelper.getSortIdField(item)
        mSortNewPosition = numPicker.value
        mSortOriginalPosition = numPicker.value
        numPicker.setOnValueChangedListener { _, _, newVal ->
            mSortNewPosition = newVal
        }
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = RSSettingsDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"
        private val KEY_MEDIA_ITEM = CLASS_NAME + "_KEY_MEDIA_ITEM"
        private val KEY_PARENT_ID = CLASS_NAME + "_KEY_PARENT_ID"
        private val KEY_MAX_SORT_ID = CLASS_NAME + "_KEY_MAX_SORT_ID"

        @UnstableApi
        fun provideMediaItem(
            bundle: Bundle,
            mediaItem: MediaItem,
            parentId: String, maxSortId: Int
        ) {
            bundle.putBundle(KEY_MEDIA_ITEM, mediaItem.toBundle())
            bundle.putString(KEY_PARENT_ID, parentId)
            bundle.putInt(KEY_MAX_SORT_ID, maxSortId)
        }

        private fun extractParentId(bundle: Bundle?): String {
            if (bundle == null) {
                return AppUtils.EMPTY_STRING
            }
            return if (!bundle.containsKey(KEY_PARENT_ID)) {
                AppUtils.EMPTY_STRING
            } else bundle.getString(KEY_PARENT_ID, AppUtils.EMPTY_STRING)
        }

        private fun extractMaxId(bundle: Bundle?): Int {
            if (bundle == null) {
                return 1000
            }
            return if (!bundle.containsKey(KEY_MAX_SORT_ID)) {
                1000
            } else bundle.getInt(KEY_MAX_SORT_ID, 1000)
        }

        @UnstableApi
        private fun extractMediaItem(bundle: Bundle?): MediaItem? {
            if (bundle == null) {
                return null
            }
            return if (!bundle.containsKey(KEY_MEDIA_ITEM)) {
                null
            } else MediaItem.CREATOR.fromBundle(bundle.getBundle(KEY_MEDIA_ITEM) ?: Bundle())
        }
    }
}
