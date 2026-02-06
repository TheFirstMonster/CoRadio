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

package app.coradio.tv.dependencies

import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.dependencies.LatestRadioStationStorageDependency
import app.coradio.shared.model.storage.LatestRadioStationStorage
import app.coradio.tv.view.activity.TvMainActivity
import app.coradio.tv.view.activity.TvMainActivityPresenter
import app.coradio.tv.view.activity.TvMainActivityPresenterImpl
import java.util.concurrent.atomic.AtomicBoolean

object DependencyRegistryTv : LatestRadioStationStorageDependency {

    private lateinit var sTvMainActivityPresenter: TvMainActivityPresenter
    private lateinit var sLatestRadioStationStorage: LatestRadioStationStorage

    @Volatile
    private var sInit = AtomicBoolean(false)

    /**
     * Init with application's context only!
     */
    fun init() {
        if (sInit.get()) {
            return
        }

        DependencyRegistryCommon.injectLatestRadioStationStorage(this)
        sTvMainActivityPresenter = TvMainActivityPresenterImpl(
            sLatestRadioStationStorage
        )

        sInit.set(true)
    }

    override fun configureWith(latestRadioStationStorage: LatestRadioStationStorage) {
        sLatestRadioStationStorage = latestRadioStationStorage
    }

    fun inject(dependency: TvMainActivity) {
        dependency.configureWith(sTvMainActivityPresenter)
    }
}
