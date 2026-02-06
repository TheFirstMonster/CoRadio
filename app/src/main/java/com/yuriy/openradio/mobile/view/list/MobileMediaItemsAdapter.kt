/*
 * Copyright 2017-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.mobile.view.list

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.xenione.libs.swipemaker.SwipeLayout
import app.coradio.mobile.R
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.presenter.MediaPresenter
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.MediaItemHelper
import app.coradio.shared.utils.gone
import app.coradio.shared.view.list.MediaItemViewHolder
import app.coradio.shared.view.list.MediaItemsAdapter

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/18/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class MobileMediaItemsAdapter(private var mContext: Context, private val mMediaPresenter: MediaPresenter) :
    MediaItemsAdapter() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemViewHolder {
        return MediaItemViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_both_side_swipe, parent, false),
            R.id.item_root, R.id.name_view, R.id.description_view, R.id.img_view, R.id.favorite_btn_view,
            R.id.bitrate_view, R.id.settings_btn_view, R.id.foreground_view
        )
    }

    @UnstableApi
    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        val mediaItem = getItem(position) ?: return
        val mediaMetadata = mediaItem.mediaMetadata
        val isPlayable = mediaMetadata.isPlayable ?: false
        handleNameAndDescriptionView(holder.mNameView, holder.mDescriptionView, mediaMetadata, parentId)
        updateImage(mContext, mediaMetadata, holder.mImageView)
        updateBitrateView(
            MediaItemHelper.getBitrateField(mediaItem), holder.mBitrateView, isPlayable
        )
        holder.mFavoriteCheckView.buttonDrawable = AppCompatResources.getDrawable(
            mContext, app.coradio.R.drawable.src_favorite
        )
        if (isPlayable) {
            handleFavoriteAction(
                holder.mFavoriteCheckView, mediaItem.mediaId, mediaMetadata, mMediaPresenter.getServiceCommander()
            )
        } else {
            holder.mFavoriteCheckView.gone()
        }
        holder.mSettingsView?.setOnClickListener(OnSettingsListener(mediaItem))
        val category = mMediaPresenter.getCurrentCategory()
        (holder.mForegroundView as SwipeLayout).isDragDisabled(
            (category == MediaId.MEDIA_ID_FAVORITES_LIST || category == MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST).not()
        )
        holder.mForegroundView?.setOnClickListener(OnItemTapListener(mediaItem, position))
        var color = R.color.or_color_primary
        if (position == activeItemId) {
            color = R.color.or_color_primary_dark
        }
        holder.mForegroundView?.setBackgroundColor(ContextCompat.getColor(mContext, color))
    }

    override fun onViewRecycled(holder: MediaItemViewHolder) {
        super.onViewRecycled(holder)
        (holder.mRoot as BothSideCoordinatorLayout).sync()
    }

    private inner class OnItemTapListener(private val mItem: MediaItem, private val mPosition: Int) :
        View.OnClickListener {

        override fun onClick(view: View) {
            listener?.onItemSelected(mItem, mPosition)
        }
    }
}
