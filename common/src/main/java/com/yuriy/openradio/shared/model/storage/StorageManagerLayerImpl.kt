/*
 * Copyright 2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.model.storage

import app.coradio.shared.model.media.RadioStation
import java.util.TreeSet

class StorageManagerLayerImpl(
    private val mFavoritesStorage: FavoritesStorage,
    private val mDeviceLocalsStorage: DeviceLocalsStorage
) : StorageManagerLayer {

    override fun mergeFavorites(value: String) {
        val set = mFavoritesStorage.getAll()
        val rxSet = mFavoritesStorage.getAllFromString(value)
        val tmp = merge(set, rxSet)
        for (station in tmp) {
            mFavoritesStorage.add(station)
        }
    }

    override fun mergeDeviceLocals(value: String) {
        val set = mDeviceLocalsStorage.getAll()
        val rxSet = mDeviceLocalsStorage.getAllFromString(value)
        val tmp = merge(set, rxSet)
        for (station in tmp) {
            mDeviceLocalsStorage.add(station)
        }
    }

    override fun getAllFavoritesAsString(): String {
        return mFavoritesStorage.getAllAsString()
    }

    override fun getAllDeviceLocalsAsString(): String {
        return mDeviceLocalsStorage.getAllAsString()
    }

    private fun merge(setA: Set<RadioStation>, setB: Set<RadioStation>): Set<RadioStation> {
        val tmp = ArrayList<RadioStation>(setA)
        tmp.addAll(setB)
        tmp.sortWith { o1, o2 ->
            o1.sortId.compareTo(o2.sortId)
        }
        var counter = 0
        for (item in tmp) {
            item.sortId = counter++
        }
        return TreeSet(tmp)
    }
}
