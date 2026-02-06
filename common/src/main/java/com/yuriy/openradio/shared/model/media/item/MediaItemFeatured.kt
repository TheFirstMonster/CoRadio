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

package app.coradio.shared.model.media.item

import app.coradio.shared.model.media.item.MediaItemCommand.IUpdatePlaybackState
import app.coradio.shared.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [MediaItemFeatured] is concrete implementation of the [MediaItemCommand] that
 * designed to prepare data to display radio stations from Featured list.
 */
class MediaItemFeatured : MediaItemCommand {

    private var mJob: Job? = null

    override fun execute(
        playbackStateListener: IUpdatePlaybackState,
        dependencies: MediaItemCommandDependencies
    ) {
        mJob?.cancel()
        mJob = dependencies.mScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(MediaItemCommand.CMD_TIMEOUT_MS) {
                val list = dependencies.presenter.getFeatured()
                for (radioStation in list) {
                    dependencies.addMediaItem(
                        MediaItemBuilder.buildPlayable(radioStation)
                    )
                }
                dependencies.resultListener.onResult(dependencies.getMediaItems(), list)
            } ?: dependencies.resultListener.onResult()
        }
    }
}
