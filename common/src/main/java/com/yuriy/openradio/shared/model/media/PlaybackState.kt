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

import androidx.media3.common.Player

class PlaybackState(
    private val mPlaybackState: Int = Player.STATE_IDLE,
    private val mPlayWhenReady: Boolean = false
) {

    val isPlaying: Boolean
        get() {
            return (mPlaybackState == Player.STATE_BUFFERING
                    || mPlaybackState == Player.STATE_READY)
                    && mPlayWhenReady
        }
}
