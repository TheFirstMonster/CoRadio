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

package app.coradio.shared

import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.SleepTimerModelDependency
import app.coradio.shared.model.timer.SleepTimerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class MainAppCommonUi: MainAppCommon(), SleepTimerModelDependency {

    private lateinit var mSleepTimerModel: SleepTimerModel

    override fun configureWith(sleepTimerModel: SleepTimerModel) {
        mSleepTimerModel = sleepTimerModel
    }

    override fun onCreate() {
        super.onCreate()
        DependencyRegistryCommon.injectSleepTimerModel(this)
        DependencyRegistryCommonUi.init(applicationContext)
        mAppScope.launch(Dispatchers.IO) {
            mSleepTimerModel.init()
        }
    }
}
