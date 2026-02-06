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
package app.coradio.tv.view.list

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import app.coradio.shared.presenter.MediaPresenter
import app.coradio.shared.utils.MediaItemHelper
import app.coradio.shared.utils.gone
import app.coradio.shared.utils.visible
import app.coradio.shared.view.list.MediaItemViewHolder
import app.coradio.shared.view.list.MediaItemsAdapter
import app.coradio.tv.R

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/18/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class TvMediaItemsAdapter(private var mContext: Context, private val mMediaPresenter: MediaPresenter) :
    MediaItemsAdapter() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemViewHolder {
        return MediaItemViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.tv_category_list_item, parent, false),
            R.id.tv_root_view, R.id.tv_name_view, R.id.tv_description_view, R.id.tv_img_view,
            R.id.tv_favorite_btn_view, R.id.tv_bitrate_view, R.id.tv_settings_btn_view, -1
        )
    }

    @UnstableApi
    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        val mediaItem = getItem(position) ?: return
        val mediaMetadata = mediaItem.mediaMetadata
        val isPlayable = mediaMetadata.isPlayable ?: false
        holder.mRoot?.setOnClickListener {
            listener?.onItemSelected(mediaItem, position)
        }
        handleNameAndDescriptionView(holder.mNameView, holder.mDescriptionView, mediaMetadata, parentId)
        updateImage(mContext, mediaMetadata, holder.mImageView)
        updateBitrateView(
            MediaItemHelper.getBitrateField(mediaItem), holder.mBitrateView, isPlayable
        )
        if (isPlayable) {
            handleFavoriteAction(
                holder.mFavoriteCheckView, mediaItem.mediaId, mediaMetadata, mMediaPresenter.getServiceCommander()
            )
            holder.mSettingsView?.setOnClickListener(OnSettingsListener(mediaItem))
            holder.mSettingsView?.visible()
        } else {
            holder.mFavoriteCheckView.gone()
            holder.mSettingsView?.gone()
        }
        var selected = false
        if (position == activeItemId) {
            selected = true
            holder.mRoot?.requestFocus()
        }
        holder.mRoot?.isSelected = selected
    }
}
