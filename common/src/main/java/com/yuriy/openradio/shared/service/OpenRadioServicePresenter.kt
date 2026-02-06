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
package app.coradio.shared.service

import android.content.Context
import app.coradio.shared.model.cast.CastLayer
import app.coradio.shared.model.eq.EqualizerLayer
import app.coradio.shared.model.media.Category
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.item.MediaItemCommand
import app.coradio.shared.model.net.NetworkMonitorListener
import app.coradio.shared.model.timer.SleepTimerModel
import app.coradio.shared.model.translation.MediaIdBuilder
import app.coradio.shared.model.translation.MediaIdBuilderDefault
import app.coradio.shared.service.location.Country

interface CoRadioServicePresenter {

    fun getMediaItemCommand(commandId: String): MediaItemCommand?

    fun startNetworkMonitor(context: Context, listener: NetworkMonitorListener)

    fun stopNetworkMonitor(context: Context)

    fun isMobileNetwork(): Boolean

    fun getCastLayer(): CastLayer

    fun getUseMobile(): Boolean

    fun getStationsInCategory(categoryId: String, pageNumber: Int): Set<RadioStation>

    fun getStationsByCountry(countryCode: String, pageNumber: Int): Set<RadioStation>

    fun getNewStations(): Set<RadioStation>

    fun getPopularStations(): Set<RadioStation>

    fun getSearchStations(query: String, mediaIdBuilder: MediaIdBuilder = MediaIdBuilderDefault()): Set<RadioStation>

    fun getAllCategories(): Set<Category>

    fun getAllCountries(): Set<Country>

    fun getAllFavorites(): Set<RadioStation>

    fun getAllDeviceLocal(): Set<RadioStation>

    fun getFeatured(): Set<RadioStation>

    fun getLastRadioStation(): RadioStation

    fun getEqualizerLayer(): EqualizerLayer

    fun setLastRadioStation(radioStation: RadioStation)

    fun getCountryCode(): String

    fun isRadioStationFavorite(radioStation: RadioStation): Boolean

    fun updateRadioStationFavorite(radioStation: RadioStation)

    fun updateRadioStationFavorite(radioStation: RadioStation, isFavorite: Boolean)

    fun updateSortIds(mediaId: String, sortId: Int, categoryMediaId: String)

    fun getSleepTimerModel(): SleepTimerModel

    /**
     * Clear resources related to service provider, such as persistent or in memory storage, etc ...
     */
    fun clear()

    /**
     * Close resources related to service provider, such as connections, streams, etc ...
     */
    fun close()
}
