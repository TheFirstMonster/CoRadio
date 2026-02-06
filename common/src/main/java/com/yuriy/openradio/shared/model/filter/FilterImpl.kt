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

package app.coradio.shared.model.filter

import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.utils.AppUtils

/**
 * Default implementation of the [Filter] interface.
 */
class FilterImpl : Filter {

    private val mData = arrayOf(
        Item("stream.radiojar.com/z4qyckhr9druv", homePage = "radiosindia.com"),
        Item("air.pc.cdn.bitgravity.com/air/live/pbaudio174"),
        Item("air.pc.cdn.bitgravity.com/air/live/pbaudio175"),
        Item("playerservices.streamtheworld.com/api/livestream-redirect/HYD_TEL_GSTAAC".lowercase()),
    )

    override fun filter(radioStation: RadioStation): Boolean {
        return mData.any { item ->
            val variant = radioStation.mediaStream.getVariant(0)
            val url = variant.url.lowercase()
            val homePage = radioStation.homePage.lowercase()

            (item.streamUrl.isNotEmpty() && url.contains(item.streamUrl))
                    || (item.homePage.isNotEmpty() && homePage.contains(item.homePage))
        }
    }

    private data class Item(
        val streamUrl: String = AppUtils.EMPTY_STRING,
        val homePage: String = AppUtils.EMPTY_STRING
    )
}
