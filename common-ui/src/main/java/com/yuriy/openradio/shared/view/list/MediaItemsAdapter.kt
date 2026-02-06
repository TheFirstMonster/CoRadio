/*
 * Copyright 2017-2020 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.view.list

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.model.ServiceCommander
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.storage.images.ImagesStore
import app.coradio.shared.service.CoRadioService
import app.coradio.shared.service.CoRadioStore
import app.coradio.shared.utils.MediaItemHelper
import app.coradio.shared.utils.gone
import app.coradio.shared.utils.setImageBitmap
import app.coradio.shared.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/18/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
abstract class MediaItemsAdapter : RecyclerView.Adapter<MediaItemViewHolder>() {

    interface Listener {
        fun onItemSettings(item: MediaItem)
        fun onItemSelected(item: MediaItem, position: Int)
    }

    private val mAdapterData = ListAdapterData<MediaItem>()
    private var mActiveItemId = DependencyRegistryCommon.UNKNOWN_ID
    var parentId = MediaId.MEDIA_ID_ROOT
    var listener: Listener? = null

    /**
     * The currently selected / active Item Id.
     */
    var activeItemId: Int
        get() {
            return mActiveItemId
        }
        set(value) {
            mActiveItemId = value
        }

    override fun getItemCount(): Int {
        return mAdapterData.itemsCount
    }

    fun getItem(position: Int): MediaItem? {
        return mAdapterData.getItem(position)
    }

    override fun onViewRecycled(holder: MediaItemViewHolder) {
        super.onViewRecycled(holder)
        holder.mImageView.setImageResource(app.coradio.R.color.or_color_transparent)
    }

    fun removeListener() {
        listener = null
    }

    /**
     * Add [MediaItem]s into the collection.
     *
     * @param value [MediaItem]s.
     */
    fun addAll(value: List<MediaItem>) {
        mAdapterData.addAll(value)
    }

    /**
     * Clear adapter data.
     */
    fun clearData() {
        mAdapterData.clear()
    }

    open fun clear() {
        clearData()
    }

    @UnstableApi
    inner class OnSettingsListener(private val mItem: MediaItem) : View.OnClickListener {

        override fun onClick(view: View) {
            listener?.onItemSettings(mItem)
        }
    }

    companion object {

        fun updateBitrateView(
            bitrate: Int,
            view: TextView?,
            isPlayable: Boolean
        ) {
            if (view == null) {
                return
            }
            if (isPlayable) {
                if (bitrate != 0) {
                    view.visible()
                    val bitrateStr = bitrate.toString() + "kb/s"
                    view.text = bitrateStr
                } else {
                    view.gone()
                }
            } else {
                view.gone()
            }
        }

        /**
         * Handle view of list item responsible to display Title and Description.
         * Different categories requires different handle approaches.
         *
         * @param nameView
         * @param descriptionView
         * @param mediaMetadata
         * @param parentId
         */
        fun handleNameAndDescriptionView(
            nameView: TextView,
            descriptionView: TextView,
            mediaMetadata: MediaMetadata,
            parentId: String
        ) {
            nameView.text = mediaMetadata.title
            descriptionView.text = mediaMetadata.subtitle
            val layoutParams = nameView.layoutParams as RelativeLayout.LayoutParams
            if (MediaId.MEDIA_ID_ROOT == parentId) {
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
                descriptionView.gone()
            } else {
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.LEFT_OF)
                descriptionView.visible()
            }
            nameView.layoutParams = layoutParams
        }

        /**
         * Updates an image view of the Media Item.
         *
         * @param mediaMetadata Media Description of the Media Item.
         * @param view        Image View to apply image to.
         */
        fun updateImage(context: Context, mediaMetadata: MediaMetadata, view: ImageView) {
            view.visible()
            // Show placeholder before load an image.
            view.setImageResource(app.coradio.R.drawable.ic_radio_station)
            val iconId = MediaItemHelper.getDrawableId(mediaMetadata.extras)
            if (MediaItemHelper.isDrawableIdValid(iconId)) {
                view.setImageResource(iconId)
            }
            val imageUrl = ImagesStore.getImageUrl(mediaMetadata.artworkUri)
            if (imageUrl.isEmpty()) {
                return
            }
            val imageUri = mediaMetadata.artworkUri ?: return
            val id = ImagesStore.getId(imageUri)
            // Mark view by tag, later on, when image downloaded and callback invoked in presenter
            // it will be possible to re-open stream and apply bytes to correct image view.
            view.tag = id
            context.contentResolver.openInputStream(imageUri)?.use {
                // Get bytes if available. If not, the callback in presenter will hook downloaded bytes
                // later on.
                view.setImageBitmap(it.readBytes())
            }
        }

        /**
         * Handle "Add | Remove to | from Favorites".
         *
         * @param checkBox Favorite check box view.
         * @param mediaId Media Id.
         * @param mediaMetadata
         * @param serviceCommander
         */
        @UnstableApi
        fun handleFavoriteAction(
            checkBox: CheckBox, mediaId: String, mediaMetadata: MediaMetadata, serviceCommander: ServiceCommander
        ) {
            checkBox.isChecked = MediaItemHelper.isFavoriteField(mediaMetadata)
            checkBox.visible()
            checkBox.setOnClickListener { view: View ->
                val isChecked = (view as CheckBox).isChecked
                MediaItemHelper.updateFavoriteField(mediaMetadata, isChecked)
                val bundle = CoRadioStore.makeUpdateIsFavoriteBundle(mediaId)
                CoroutineScope(Dispatchers.Main).launch {
                    serviceCommander.sendCommand(
                        if (isChecked) CoRadioService.CMD_FAVORITE_OFF else CoRadioService.CMD_FAVORITE_ON,
                        bundle
                    )
                }
            }
        }
    }
}
