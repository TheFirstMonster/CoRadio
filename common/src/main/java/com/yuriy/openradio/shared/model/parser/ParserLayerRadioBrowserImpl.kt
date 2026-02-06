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

package app.coradio.shared.model.parser

import android.net.Uri
import app.coradio.shared.model.filter.Filter
import app.coradio.shared.model.media.Category
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.setVariant
import app.coradio.shared.model.translation.MediaIdBuilder
import app.coradio.shared.service.location.Country
import app.coradio.shared.service.location.LocationService
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.TreeSet

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/15/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * This is implementation of [ParserLayer] that designed to works with JSON as input format.
 *
 * @param mFilter Filter to exclude certain radio stations.
 */
class ParserLayerRadioBrowserImpl(private val mFilter: Filter) : ParserLayer {

    companion object {
        private const val TAG = "PLRBI"
        private const val KEY_STATION_UUID = "stationuuid"
        private const val KEY_NAME = "name"
        private const val KEY_COUNTRY = "country"
        /**
         * 2 letters, uppercase.
         */
        private const val KEY_COUNTRY_CODE = "countrycode"
        private const val KEY_BIT_RATE = "bitrate"
        private const val KEY_URL = "url"
        private const val KEY_URL_RESOLVED = "url_resolved"
        private const val KEY_FAV_ICON = "favicon"
        private const val KEY_STATIONS_COUNT = "stationcount"
        private const val KEY_HOME_PAGE = "homepage"
        private const val KEY_LAST_CHECK_OK_TIME = "lastcheckoktime"
        private const val KEY_LAST_CHECK_OK = "lastcheckok"
        private const val KEY_CODEC = "codec"
        private const val KEY_ISO_3166_1 = "iso_3166_1"
    }

    override fun getRadioStation(data: String, mediaIdBuilder: MediaIdBuilder, uri: Uri): RadioStation {
        val list = getRadioStations(data, mediaIdBuilder, uri)
        return if (list.isNotEmpty()) list.first() else RadioStation.INVALID_INSTANCE
    }

    override fun getRadioStations(data: String, mediaIdBuilder: MediaIdBuilder, uri: Uri): Set<RadioStation> {
        if (data.isEmpty()) {
            AppLogger.e("$TAG get RSs, data empty")
            return emptySet()
        }
        val array = try {
            JSONArray(data)
        } catch (e: Exception) {
            AppLogger.e("$TAG  get RSs, to JSON Array, data '$data'", e)
            return emptySet()
        }
        val result = TreeSet<RadioStation>()
        for (i in 0 until array.length()) {
            val jsonObject = try {
                array[i] as JSONObject
            } catch (e: Exception) {
                AppLogger.e("$TAG get RSs, data:$data", e)
                continue
            }
            val radioStation = getRadioStation(jsonObject, mediaIdBuilder)
            if (radioStation.isMediaStreamEmpty()) {
                continue
            }
            result.add(radioStation)
        }
        return result
    }

    override fun getAllCategories(data: String): Set<Category> {
        if (data.isEmpty()) {
            AppLogger.e("$TAG get ACs, data empty")
            return emptySet()
        }
        val array = try {
            JSONArray(data)
        } catch (e: Exception) {
            AppLogger.e("$TAG get ACs, to JSON Array, data:$data", e)
            return emptySet()
        }
        val result = TreeSet<Category>()
        for (i in 0 until array.length()) {
            val jsonObject = try {
                array[i] as JSONObject
            } catch (e: Exception) {
                AppLogger.e("$TAG get AC, data:$data", e)
                continue
            }
            if (jsonObject.has(KEY_NAME)) {
                val id = jsonObject.getString(KEY_NAME)
                val title = jsonObject.getString(KEY_NAME)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                var stationsCount = 0
                if (jsonObject.has(KEY_STATIONS_COUNT)) {
                    stationsCount = jsonObject.getInt(KEY_STATIONS_COUNT)
                }
                result.add(Category(id, title, stationsCount))
            }
        }
        return result
    }

    override fun getAllCountries(data: String): Set<Country> {
        val array = try {
            JSONArray(data)
        } catch (e: Exception) {
            AppLogger.e("$TAG to JSON Array, data:$data", e)
            return emptySet()
        }
        val set = TreeSet<Country>()
        for (i in 0 until array.length()) {
            val jsonObject = try {
                array[i] as JSONObject
            } catch (e: Exception) {
                AppLogger.e("$TAG getAllCountries, data:$data", e)
                continue
            }
            if (jsonObject.has(KEY_ISO_3166_1)) {
                val iso = jsonObject.getString(KEY_ISO_3166_1)
                val name = LocationService.COUNTRY_CODE_TO_NAME[iso]
                if (name != null) {
                    set.add(Country(name, iso))
                } else {
                    AppLogger.w("$TAG Missing country of $iso")
                }
            }
        }
        return set
    }

    private fun getRadioStation(jsonObject: JSONObject, mediaIdBuilder: MediaIdBuilder): RadioStation {
        val uuid = mediaIdBuilder.build(JsonUtils.getStringValue(jsonObject, KEY_STATION_UUID))
        if (uuid == AppUtils.EMPTY_STRING) {
            AppLogger.e("No UUID present in data:$jsonObject")
            return RadioStation.INVALID_INSTANCE
        }
        val radioStation = RadioStation.makeDefaultInstance(uuid)
        radioStation.name = JsonUtils.getStringValue(jsonObject, KEY_NAME)
        radioStation.homePage = JsonUtils.getStringValue(jsonObject, KEY_HOME_PAGE)
        radioStation.country = JsonUtils.getStringValue(jsonObject, KEY_COUNTRY)
        radioStation.countryCode = JsonUtils.getStringValue(jsonObject, KEY_COUNTRY_CODE)
        radioStation.imageUrl = JsonUtils.getStringValue(jsonObject, KEY_FAV_ICON)
        radioStation.lastCheckOkTime = JsonUtils.getStringValue(jsonObject, KEY_LAST_CHECK_OK_TIME)
        radioStation.urlResolved = JsonUtils.getStringValue(jsonObject, KEY_URL_RESOLVED)
        radioStation.codec = JsonUtils.getStringValue(jsonObject, KEY_CODEC)
        radioStation.lastCheckOk = JsonUtils.getIntValue(jsonObject, KEY_LAST_CHECK_OK)
        if (jsonObject.has(KEY_URL)) {
            var bitrate = 0
            if (jsonObject.has(KEY_BIT_RATE)) {
                bitrate = jsonObject.getInt(KEY_BIT_RATE)
            }
            radioStation.setVariant(bitrate, jsonObject.getString(KEY_URL))
        }
        if (mFilter.filter(radioStation)) {
            AppLogger.e("Exclude:$radioStation")
            return RadioStation.INVALID_INSTANCE
        }
        return radioStation
    }
}
