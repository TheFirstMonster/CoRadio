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

package app.coradio.shared.utils

import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 6/4/15
 * E-Mail: chernyshov.yuriy@gmail.com
 */
object MediaItemHelper {

    private const val DRAWABLE_ID_UNDEFINED = MediaSessionCompat.QueueItem.UNKNOWN_ID
    private const val KEY_IS_FAVORITE = "KEY_IS_FAVORITE"
    private const val KEY_IS_LOCAL = "KEY_IS_LOCAL"
    private const val KEY_SORT_ID = "KEY_SORT_ID"
    private const val KEY_BITRATE = "KEY_BITRATE"
    private const val KEY_DRAWABLE_ID = "DRAWABLE_ID"

    fun setDrawableId(bundle: Bundle?, drawableId: Int) {
        bundle?.putInt(KEY_DRAWABLE_ID, drawableId)
    }

    fun getDrawableId(bundle: Bundle?): Int {
        return bundle?.getInt(KEY_DRAWABLE_ID, DRAWABLE_ID_UNDEFINED)
            ?: DRAWABLE_ID_UNDEFINED
    }

    fun isDrawableIdValid(drawableId: Int): Boolean {
        return drawableId != DRAWABLE_ID_UNDEFINED
    }

    fun updateBitrateField(bundle: Bundle, bitrate: Int) {
        bundle.putInt(KEY_BITRATE, bitrate)
    }

    fun getBitrateField(mediaItem: MediaItem?): Int {
        return mediaItem?.mediaMetadata?.extras?.getInt(KEY_BITRATE, 0) ?: 0
    }

    /**
     * Sets key that indicates Radio Station is in favorites.
     *
     * @param mediaMetadata [MediaMetadata].
     * @param isFavorite Whether Item is in Favorites.
     */
    fun updateFavoriteField(mediaMetadata: MediaMetadata?, isFavorite: Boolean) {
        val bundle = mediaMetadata?.extras ?: return
        updateFavoriteField(bundle, isFavorite)
    }

    fun updateFavoriteField(bundle: Bundle, isFavorite: Boolean) {
        bundle.putBoolean(KEY_IS_FAVORITE, isFavorite)
    }

    /**
     * Sets key that indicates Radio Station is in Local Radio Stations.
     *
     * @param bundle
     * @param isLocal   Whether Item is in Local Radio Stations.
     */
    fun updateLocalRadioStationField(bundle: Bundle, isLocal: Boolean) {
        bundle.putBoolean(KEY_IS_LOCAL, isLocal)
    }

    fun updateSortIdField(bundle: Bundle, sortId: Int) {
        bundle.putInt(KEY_SORT_ID, sortId)
    }

    /**
     * Gets `true` if Item is Favorite, `false` - otherwise.
     *
     * @param mediaMetadata [MediaMetadata].
     * @return `true` if Item is Favorite, `false` - otherwise.
     */
    fun isFavoriteField(mediaMetadata: MediaMetadata?): Boolean {
        return mediaMetadata?.extras?.getBoolean(KEY_IS_FAVORITE, false) ?: false
    }

    /**
     * Extracts Sort Id field from the [MediaItem].
     *
     * @param mediaItem [MediaItem] to extract
     * Sort Id from.
     * @return Extracted Sort Id or -1.
     */
    fun getSortIdField(mediaItem: MediaItem?): Int {
        return if (mediaItem == null) {
            MediaSessionCompat.QueueItem.UNKNOWN_ID
        } else getSortIdField(mediaItem.mediaMetadata.extras)
    }

    /**
     * Extracts Sort Id field from the [MediaDescriptionCompat].
     *
     * @param extras [Bundle] to extract Sort Id from.
     * @return Extracted Sort Id or -1.
     */
    private fun getSortIdField(extras: Bundle?): Int {
        if (extras == null) {
            return MediaSessionCompat.QueueItem.UNKNOWN_ID
        }
        return extras.getInt(KEY_SORT_ID, MediaSessionCompat.QueueItem.UNKNOWN_ID)
    }

    /**
     * Try to extract useful information from the media description. In good case - this is metadata associated
     * with the stream, subtitles otherwise, in worse case - default string.
     *
     * @param value Media description to parse.
     *
     * @return Display description.
     */
    fun getDisplayDescription(value: MediaMetadata, defaultValue: String): String {
        val descChars = value.subtitle ?: return defaultValue
        var result = descChars.toString()
        if (result.isNotEmpty()) {
            return result
        }
        val subTitleChars = value.description ?: return defaultValue
        result = subTitleChars.toString()
        if (result.isNotEmpty()) {
            return result
        }
        if (value.extras != null) {
            result = value.extras!!.getString(MediaMetadataCompat.METADATA_KEY_ARTIST, defaultValue)
        }
        return result.ifEmpty { defaultValue }
    }
}
