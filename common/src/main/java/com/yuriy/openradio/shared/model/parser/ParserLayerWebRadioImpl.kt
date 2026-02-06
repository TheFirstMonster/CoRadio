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

package app.coradio.shared.model.parser

import android.net.Uri
import app.coradio.shared.model.media.Category
import app.coradio.shared.model.media.MediaStream
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.isInvalid
import app.coradio.shared.model.media.setVariant
import app.coradio.shared.model.net.UrlLayerWebRadioImpl
import app.coradio.shared.model.translation.MediaIdBuilder
import app.coradio.shared.service.location.Country
import app.coradio.shared.service.location.LocationService
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap
import java.util.TreeSet

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/15/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * This is implementation of [ParserLayer] that designed to works with JSON as input format.
 */
class ParserLayerWebRadioImpl(private val mCountriesCache:Set<Country>) : ParserLayer {

    companion object {
        private const val TAG = "PLWRI"
        private const val KEY_GENRE = "Genre"
        private const val KEY_NAME = "Name"
        private const val KEY_URL = "StreamUri"
        private const val KEY_CODEC = "Codec"
        private const val KEY_BIT_RATE = "Bitrate"
        private const val KEY_HOME_PAGE = "Homepage"
        private const val KEY_COUNTRY = "Country"
        private const val KEY_DESCRIPTION = "Description"
        private const val KEY_IMAGE = "Image"
        private const val IMG_URL_PREFIX = "https://jcorporation.github.io/webradiodb/db/pics/"
    }

    override fun getRadioStation(data: String, mediaIdBuilder: MediaIdBuilder, uri: Uri): RadioStation {
        val list = getRadioStations(data, mediaIdBuilder, uri)
        return if (list.isNotEmpty()) list.first() else RadioStation.INVALID_INSTANCE
    }

    override fun getRadioStations(data: String, mediaIdBuilder: MediaIdBuilder, uri: Uri): Set<RadioStation> {
        val jsonData = try {
            JSONObject(data)
        } catch (e: Exception) {
            AppLogger.e("$TAG to JSON, data:$data", e)
            return emptySet()
        }
        val categoryId = getCategoryId(uri)
        val countryId = getCountryId(uri)
        val searchId = getSearchId(uri)
        val result = TreeSet<RadioStation>()
        for (uuid in jsonData.keys()) {
            val jsonObject = try {
                jsonData[uuid] as JSONObject
            } catch (e: Exception) {
                AppLogger.e("$TAG get stations, data:$data", e)
                continue
            }
            if (categoryId != AppUtils.EMPTY_STRING) {
                val radioStation = getRadioStationByGenre(uuid, jsonObject, categoryId)
                if (radioStation.isInvalid().not()) {
                    result.add(radioStation)
                }
            } else if (countryId != AppUtils.EMPTY_STRING) {
                var countryName = AppUtils.EMPTY_STRING
                for (country in mCountriesCache) {
                    if (country.code == countryId) {
                        countryName = country.name
                    }
                }
                val radioStation = getRadioStationByCountry(uuid, jsonObject, countryName)
                if (radioStation.isInvalid().not()) {
                    result.add(radioStation)
                }
            } else if (searchId != AppUtils.EMPTY_STRING) {
                val radioStation = getRadioStationByName(uuid, jsonObject, searchId)
                if (radioStation.isInvalid().not()) {
                    result.add(radioStation)
                }
            } else {
                AppLogger.e("$TAG no criteria specified, return empty set")
            }
        }
        return result
    }

    override fun getAllCategories(data: String): Set<Category> {
        val jsonData = try {
            JSONObject(data)
        } catch (e: Exception) {
            AppLogger.e("$TAG to JSON, data:$data", e)
            return emptySet()
        }
        val result = TreeSet<Category>()
        val tmp = TreeMap<String, Int>()
        for (i in jsonData.keys()) {
            val jsonObject = try {
                jsonData[i] as JSONObject
            } catch (e: Exception) {
                AppLogger.e("$TAG get categories, data:$data", e)
                continue
            }
            if (jsonObject.has(KEY_GENRE)) {
                val genres = try {
                    jsonObject.getJSONArray(KEY_GENRE)
                } catch (e: Exception) {
                    AppLogger.e("$TAG get genre, data:$data", e)
                    continue
                }
                for (j in 0 until genres.length()) {
                    val genre = genres.getString(j)
                    if (tmp.containsKey(genre)) {
                        tmp[genre] = tmp[genre]!! + 1
                    } else {
                        tmp[genre] = 1
                    }
                }
            }
        }
        for (entry in tmp.entries) {
            result.add(Category(entry.key, entry.key, entry.value))
        }
        return result
    }

    @Synchronized
    override fun getAllCountries(data: String): Set<Country> {
        val jsonData = try {
            JSONArray(data)
        } catch (e: Exception) {
            AppLogger.e("$TAG to JSON Array, data:$data", e)
            return emptySet()
        }
        val set = TreeSet<Country>()
        for (j in 0 until jsonData.length()) {
            val name = jsonData.getString(j)
            val iso = LocationService.COUNTRY_NAME_TO_CODE[name]
            if (iso != null) {
                val country = Country(name, iso)
                set.add(country)
            } else {
                AppLogger.w("$TAG Missing country of $name")
            }
        }
        return set
    }

    private fun getRadioStation(jsonObject: JSONObject, uuid: String): RadioStation {
        val radioStation = RadioStation.makeDefaultInstance(uuid)
        radioStation.name = JsonUtils.getStringValue(jsonObject, KEY_NAME)
        radioStation.homePage = JsonUtils.getStringValue(jsonObject, KEY_HOME_PAGE)
        radioStation.country = JsonUtils.getStringValue(jsonObject, KEY_COUNTRY)
        radioStation.imageUrl = IMG_URL_PREFIX + JsonUtils.getStringValue(jsonObject, KEY_IMAGE)
        radioStation.codec = JsonUtils.getStringValue(jsonObject, KEY_CODEC)
        radioStation.description = JsonUtils.getStringValue(jsonObject, KEY_DESCRIPTION)
        var bitrate = MediaStream.BIT_RATE_DEFAULT
        if (jsonObject.has(KEY_BIT_RATE)) {
            bitrate = jsonObject.getInt(KEY_BIT_RATE)
        }
        radioStation.setVariant(bitrate, jsonObject.getString(KEY_URL))
        return radioStation
    }

    private fun getCategoryId(uri: Uri): String {
        return getBrowseId(uri, UrlLayerWebRadioImpl.KEY_CATEGORY_ID)
    }

    private fun getCountryId(uri: Uri): String {
        return getBrowseId(uri, UrlLayerWebRadioImpl.KEY_COUNTRY_ID)
    }

    private fun getSearchId(uri: Uri): String {
        return getBrowseId(uri, UrlLayerWebRadioImpl.KEY_SEARCH_ID)
    }

    private fun getBrowseId(uri: Uri, key: String): String {
        val pair = uri.toString().split(key)
        if (pair.size != 2) {
            return AppUtils.EMPTY_STRING
        }
        return pair[1]
    }

    private fun getRadioStationByGenre(uuid: String, jsonObject: JSONObject, categoryId: String): RadioStation {
        if (jsonObject.has(KEY_GENRE).not()) {
            return RadioStation.INVALID_INSTANCE
        }
        val genres = try {
            jsonObject.getJSONArray(KEY_GENRE)
        } catch (e: Exception) {
            AppLogger.e("$TAG get genres", e)
            return RadioStation.INVALID_INSTANCE
        }
        for (j in 0 until genres.length()) {
            val genre = genres.getString(j)
            if (genre != categoryId) {
                continue
            }
            return getRadioStation(jsonObject, uuid)
        }
        return RadioStation.INVALID_INSTANCE
    }

    private fun getRadioStationByCountry(uuid: String, jsonObject: JSONObject, countryName: String): RadioStation {
        if (jsonObject.has(KEY_COUNTRY).not()) {
            return RadioStation.INVALID_INSTANCE
        }
        val country = try {
            jsonObject.getString(KEY_COUNTRY)
        } catch (e: Exception) {
            AppLogger.e("$TAG get country", e)
            return RadioStation.INVALID_INSTANCE
        }
        if (countryName != country) {
            return RadioStation.INVALID_INSTANCE
        }
        return getRadioStation(jsonObject, uuid)
    }

    private fun getRadioStationByName(uuid: String, jsonObject: JSONObject, searchId: String): RadioStation {
        if (jsonObject.has(KEY_COUNTRY).not()) {
            return RadioStation.INVALID_INSTANCE
        }
        val name = try {
            jsonObject.getString(KEY_NAME)
        } catch (e: Exception) {
            AppLogger.e("$TAG get name", e)
            return RadioStation.INVALID_INSTANCE
        }
        if (name.lowercase().contains(searchId.lowercase()).not()) {
            return RadioStation.INVALID_INSTANCE
        }
        return getRadioStation(jsonObject, uuid)
    }
}
