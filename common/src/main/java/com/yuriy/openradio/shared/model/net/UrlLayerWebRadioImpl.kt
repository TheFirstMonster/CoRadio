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

package app.coradio.shared.model.net

import android.net.Uri
import androidx.core.util.Pair
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.NetUtils
import java.net.MalformedURLException
import java.net.URL

/**
 * Use single url to get whole dataset and parse it into the data object.
 */
class UrlLayerWebRadioImpl : UrlLayer {

    override fun getConnectionUrl(uri: Uri, parameters: List<Pair<String, String>>): URL? {
        return try {
            URL(uri.toString())
        } catch (exception: MalformedURLException) {
            AppLogger.e(
                "$TAG getUrl ${
                    NetUtils.createExceptionMessage(
                        uri,
                        parameters
                    )
                }", exception
            )
            null
        }
    }

    override fun getAllCategoriesUrl(): Uri {
        return URI
    }

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Uri {
        val url = "$URL$KEY_CATEGORY_ID$categoryId"
        return Uri.parse(url)
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Uri {
        val url = "$URL$KEY_COUNTRY_ID$countryCode"
        return Uri.parse(url)
    }

    override fun getPopularStations(): Uri {
        return URI
    }

    override fun getNewStations(): Uri {
        return URI
    }

    override fun getSearchUrl(query: String): Uri {
        val url = "$URL$KEY_SEARCH_ID$query"
        return Uri.parse(url)
    }

    override fun getAllCountries(): Uri {
        return URI_COUNTRIES
    }

    companion object {

        private const val TAG = "ULWRI"
        private const val URL = "https://jcorporation.github.io/webradiodb/db/index/webradios.min.json"
        private val URI = Uri.parse(URL)
        private val URI_COUNTRIES = Uri.parse(
            "https://jcorporation.github.io/webradiodb/db/index/countries.min.json"
        )

        const val KEY_CATEGORY_ID = "?categoryId="
        const val KEY_COUNTRY_ID = "?countryId="
        const val KEY_SEARCH_ID = "?searchId="
    }
}
