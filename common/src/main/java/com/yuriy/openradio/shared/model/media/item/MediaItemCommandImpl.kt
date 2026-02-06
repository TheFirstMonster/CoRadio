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

package app.coradio.shared.model.media.item

import app.coradio.R
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.item.MediaItemCommand.IUpdatePlaybackState
import app.coradio.shared.utils.MediaItemBuilder
import java.util.TreeSet

/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 14/01/18
 * E-Mail: chernyshov.yuriy@gmail.com
 */
abstract class MediaItemCommandImpl internal constructor() : MediaItemCommand {

    override fun execute(playbackStateListener: IUpdatePlaybackState, dependencies: MediaItemCommandDependencies) {

    }

    abstract fun doLoadNoDataReceived(): Boolean

    fun handleDataLoaded(
        playbackStateListener: IUpdatePlaybackState,
        dependencies: MediaItemCommandDependencies,
        set: Set<RadioStation>,
        pageNumber: Int = 0
    ) {
        if (set.isEmpty()) {
            if (doLoadNoDataReceived()) {
                dependencies.resultListener.onResult(dependencies.getMediaItems())
                playbackStateListener.updatePlaybackState(dependencies.context.getString(R.string.no_data_message))
            } else {
                dependencies.resultListener.onResult(dependencies.getMediaItems(), pageNumber = pageNumber)
            }
            return
        }
        deliverResult(dependencies, set, pageNumber)
    }

    fun deliverResult(
        dependencies: MediaItemCommandDependencies,
        set: Set<RadioStation> = TreeSet(),
        pageNumber: Int = 0
    ) {
        for (radioStation in set) {
            dependencies.addMediaItem(
                MediaItemBuilder.buildPlayable(
                    radioStation, dependencies.presenter.isRadioStationFavorite(radioStation)
                )
            )
        }
        dependencies.resultListener.onResult(dependencies.getMediaItems(), set, pageNumber)
    }
}
