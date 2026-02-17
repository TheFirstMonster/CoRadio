/*
 * Copyright 2017, 2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.model

import android.net.Uri
import app.coradio.shared.model.media.Category
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.translation.MediaIdBuilder
import app.coradio.shared.service.location.Country

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/14/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [ModelLayer] is a model interface which provides service behavior of the CoRadio.
 */
interface ModelLayer {
    /**
     * Get a list of all categories.
     *
     * @param uri [Uri] of the request.
     * @return Set of the [Category]s
     */
    fun getAllCategories(uri: Uri): Set<Category>

    /**
     * Get a list of all countries.
     *
     * @return Set of the Countries.
     */
    fun getAllCountries(uri: Uri): Set<Country>

    /**
     * Get a list of Radio Stations by provided Uri.
     *
     * @param uri [Uri] of the request.
     * @return collection of the Radio Stations.
     */
    fun getStations(uri: Uri, mediaIdBuilder: MediaIdBuilder): Set<RadioStation>

    fun getFeatured(): Set<RadioStation>
}
