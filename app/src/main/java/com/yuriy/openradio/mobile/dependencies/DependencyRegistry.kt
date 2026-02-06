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

package app.coradio.mobile.dependencies

import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.dependencies.FavoritesStorageDependency
import app.coradio.shared.dependencies.LatestRadioStationStorageDependency
import app.coradio.shared.model.storage.FavoritesStorage
import app.coradio.shared.model.storage.LatestRadioStationStorage
import java.util.concurrent.atomic.AtomicBoolean

object DependencyRegistry : FavoritesStorageDependency, LatestRadioStationStorageDependency {

    private lateinit var sFavoritesStorage: FavoritesStorage
    private lateinit var sLatestRadioStationStorage: LatestRadioStationStorage

    @Volatile
    private var sInit = AtomicBoolean(false)

    fun init() {
        if (sInit.get()) {
            return
        }

        DependencyRegistryCommon.injectFavoritesStorage(this)
        DependencyRegistryCommon.injectLatestRadioStationStorage(this)

        sInit.set(true)
    }

    override fun configureWith(favoritesStorage: FavoritesStorage) {
        sFavoritesStorage = favoritesStorage
    }

    override fun configureWith(latestRadioStationStorage: LatestRadioStationStorage) {
        sLatestRadioStationStorage = latestRadioStationStorage
    }
}
