/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.model.media

import androidx.media3.common.MediaItem

class BrowseTree {

    private val mMediaIdToChildren = mutableMapOf<String, MutableList<MediaItem>>()
    private val mMediaIdToChildrenRadioStations = mutableMapOf<String, MutableSet<RadioStation>>()
    private val mMediaIdToMediaItem = mutableMapOf<String, MediaItem>()
    private val mMediaIdToRadioStation = mutableMapOf<String, RadioStation>()

    operator fun get(mediaId: String) = mMediaIdToChildren[mediaId]

    operator fun set(mediaId: String, browseData: BrowseData) {
        mMediaIdToChildren[mediaId] = browseData.children
        mMediaIdToChildrenRadioStations[mediaId] = browseData.radioStations
        browseData.children.forEach { mediaItem ->
            run {
                mMediaIdToMediaItem[mediaItem.mediaId] = mediaItem
            }
        }
        browseData.radioStations.forEach { radioStation ->
            run {
                mMediaIdToRadioStation[radioStation.id] = radioStation
            }
        }
    }

    fun append(mediaId: String, browseData: BrowseData) {
        mMediaIdToChildren[mediaId]?.plusAssign(browseData.children)
        mMediaIdToChildrenRadioStations[mediaId]?.plusAssign(browseData.radioStations)
        browseData.children.forEach { mediaItem ->
            run {
                mMediaIdToMediaItem[mediaItem.mediaId] = mediaItem
            }
        }
        browseData.radioStations.forEach { radioStation ->
            run {
                mMediaIdToRadioStation[radioStation.id] = radioStation
            }
        }
    }

    fun getMediaItemsByMediaId(mediaId: String): MutableList<MediaItem> {
        // First search inside keys.
        for (entry in mMediaIdToChildren.entries) {
            if (entry.key == mediaId) {
                return entry.value
            }
        }
        // Then move to the children of each key.
        for (entry in mMediaIdToChildren.entries) {
            for (item in entry.value) {
                if (item.mediaId == mediaId) {
                    return entry.value
                }
            }
        }
        return mutableListOf()
    }

    fun getMediaItemByMediaId(mediaId: String): MediaItem? {
        return mMediaIdToMediaItem[mediaId]
    }

    fun getRadioStationByMediaId(mediaId: String) = mMediaIdToRadioStation[mediaId] ?: RadioStation.INVALID_INSTANCE

    fun invalidate(mediaId: String) {
        mMediaIdToChildren.remove(mediaId)?.forEach {
            mMediaIdToMediaItem.remove(it.mediaId)
        }
        mMediaIdToChildrenRadioStations.remove(mediaId)?.forEach {
            mMediaIdToRadioStation.remove(it.id)
        }
    }

    class BrowseData(val children: MutableList<MediaItem>, val radioStations: MutableSet<RadioStation>)
}
