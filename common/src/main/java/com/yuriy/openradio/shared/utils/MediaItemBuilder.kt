/*
 * Copyright 2022, 2023. The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.coradio.R
import app.coradio.shared.model.media.Category
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.media.MediaStream
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.getStreamBitrate
import app.coradio.shared.model.media.getStreamUrlFixed
import app.coradio.shared.service.location.Country
import app.coradio.shared.service.location.LocationService
import java.util.Locale

object MediaItemBuilder {

    fun buildRootMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildMediaItemListEnded(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_LIST_ENDED)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildChildCategory(context: Context, category: Category): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_child_categories)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_CHILD_CATEGORIES + category.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(category.title)
                    .setSubtitle(category.getDescription(context))
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildChildCategories(): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_radio_station_empty)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_CHILD_CATEGORIES)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildFavoritesMenuItem(context: Context): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_stars_black_24dp)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_FAVORITES_LIST)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(context.getString(R.string.favorites_list_title))
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildFeaturedMenuItem(context: Context): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_featured)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_FEATURED_LIST)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(context.getString(R.string.featured_list_title))
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildNewStationsMenuItem(context: Context): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_fiber_new_black_24dp)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_NEW_STATIONS)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(context.getString(R.string.new_stations_title))
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildBrowseMenuItem(context: Context): MediaItem {
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_BROWSE_CAR)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(context.getString(R.string.browse_title))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildPopularMenuItem(context: Context): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_trending_up_black_24dp)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_POPULAR_STATIONS)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(context.getString(R.string.popular_stations_title))
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildCategoriesMenuItem(context: Context): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_all_categories)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_ALL_CATEGORIES)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(context.getString(R.string.all_categories_title))
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildCountriesMenuItem(context: Context): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_public_black_24dp)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_COUNTRIES_LIST)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(context.getString(R.string.countries_list_title))
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildCountryMenuItem(context: Context, countryCode: String): MediaItem {
        val identifier = context.resources.getIdentifier(
            "flag_" + countryCode.lowercase(Locale.ROOT),
            "drawable", context.packageName
        )
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, identifier)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_COUNTRY_STATIONS)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(LocationService.COUNTRY_CODE_TO_NAME[countryCode])
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildCountryMenuItem(context: Context, country: Country): MediaItem {
        val identifier = context.resources.getIdentifier(
            "flag_" + country.code.lowercase(Locale.ROOT),
            "drawable", context.packageName
        )
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, identifier)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_COUNTRIES_LIST + country.code)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setTitle(country.name)
                    .setSubtitle(country.code)
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildDeviceLocalsMenuItem(context: Context): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_locals)
        return MediaItem.Builder()
            .setMediaId(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(context.getString(R.string.local_radio_stations_list_title))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setExtras(bundle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun buildPlayable(
        radioStation: RadioStation,
        isFavorite: Boolean = false,
        isLocal: Boolean = false
    ): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.updateBitrateField(bundle, radioStation.getStreamBitrate())
        MediaItemHelper.updateFavoriteField(bundle, isFavorite)
        MediaItemHelper.updateSortIdField(bundle, radioStation.sortId)
        MediaItemHelper.updateLocalRadioStationField(bundle, isLocal)
        MediaItemHelper.setDrawableId(bundle, R.drawable.ic_radio_station_empty)
        val uri = Uri.parse(radioStation.getStreamUrlFixed())
        return MediaItem.Builder()
            .setMediaId(radioStation.id)
            .setUri(uri)
            .setMimeType(AppUtils.getMimeTypeFromUri(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setTitle(radioStation.name)
                    .setSubtitle(radioStation.country)
                    .setDescription(radioStation.genre)
                    .setArtworkUri(radioStation.imageUri)
                    .setExtras(bundle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    fun buildDefaultPlayable(): MediaItem {
        val bundle = Bundle()
        MediaItemHelper.updateBitrateField(bundle, MediaStream.BIT_RATE_DEFAULT)
        val uri = Uri.parse("http://stream.mangoradio.de")
        return MediaItem.Builder()
            .setMediaId("78012206-1aa1-11e9-a80b-52543be04c81")
            .setUri(uri)
            .setMimeType(AppUtils.getMimeTypeFromUri(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setTitle("MANGORADIO")
                    .setSubtitle("Germany")
                    .setDescription("Dance")
                    .setArtworkUri(Uri.parse("https://mangoradio.de//android-icon-192x192.png"))
                    .setExtras(bundle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }
}
