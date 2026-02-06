/*
 * Copyright 2021, 2023. The "Open Radio" Project. Author: Chernyshov Yuriy
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

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

object PlayerUtils {

    /**
     * This instance is a value object to indicate end of the indexed list.
     */
    private val mMediaItemListEnded = MediaItemBuilder.buildMediaItemListEnded()

    /**
     * @param list
     * @return
     */
    fun isEndOfList(list: List<MediaItem?>?): Boolean {
        return (list == null
                || list.size == 1
                && (list[0] == null || list[0]?.mediaId === mMediaItemListEnded.mediaId))
    }

    fun playerStateToString(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "STATE_IDLE"
            Player.STATE_BUFFERING -> "STATE_BUFFERING"
            Player.STATE_READY -> "STATE_READY"
            Player.STATE_ENDED -> "STATE_ENDED"
            else -> "UNDEFINED{$state}"
        }
    }
}
