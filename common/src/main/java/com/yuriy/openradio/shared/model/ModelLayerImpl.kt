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

package app.coradio.shared.model

import android.content.Context
import android.net.Uri
import app.coradio.shared.model.media.Category
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.net.DownloaderLayer
import app.coradio.shared.model.net.NetworkLayer
import app.coradio.shared.model.parser.FeaturedParserLayer
import app.coradio.shared.model.parser.ParserLayer
import app.coradio.shared.model.storage.cache.api.ApiCache
import app.coradio.shared.model.translation.MediaIdBuilder
import app.coradio.shared.service.location.Country
import app.coradio.shared.utils.AppLogger
import app.coradio.shared.utils.AppUtils
import java.util.TreeSet
import java.util.concurrent.TimeUnit

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/15/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [ModelLayerImpl] is the main implementation of the [ModelLayer] interface.
 */
class ModelLayerImpl(
    private val mContext: Context,
    private val mDataParser: ParserLayer,
    private val mFeaturedParser: FeaturedParserLayer,
    private val mNetworkLayer: NetworkLayer,
    private val mDownloaderLayer: DownloaderLayer,
    private val mApiCachePersistent: ApiCache,
    private val mApiCacheInMemory: ApiCache
) : ModelLayer {

    private val mFeatured = TreeSet<RadioStation>()

    override fun getAllCategories(uri: Uri): Set<Category> {
        val data = downloadData(uri)
        return mDataParser.getAllCategories(data)
    }

    override fun getAllCountries(uri: Uri): Set<Country> {
        val data = downloadData(uri)
        return mDataParser.getAllCountries(data)
    }

    override fun getStations(uri: Uri, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
        val data = downloadData(uri)
        return mDataParser.getRadioStations(data, mediaIdBuilder, uri)
    }

    override fun getFeatured(): Set<RadioStation> {
        return mFeatured
    }

    /**
     * Download data as [String].
     *
     * @param uri Uri to download from.
     * @return [String]
     */
    private fun downloadData(uri: Uri): String {
        var response = AppUtils.EMPTY_STRING
        if (!mNetworkLayer.checkConnectivityAndNotify(mContext)) {
            return response
        }

        // Create key to associate response with.
        val responsesMapKey = uri.toString()

        // Fetch RAM memory first.
        response = mApiCacheInMemory[responsesMapKey]
        if (response != AppUtils.EMPTY_STRING && response != "[]") {
            return response
        }

        // Then look up data in the DB.
        response = mApiCachePersistent[responsesMapKey]
        if (response != AppUtils.EMPTY_STRING && response != "[]") {
            mApiCacheInMemory.remove(responsesMapKey)
            mApiCacheInMemory.put(responsesMapKey, response)
            return response
        }
        // Finally, go to internet.

        // Declare and initialize variable for response.
        response = String(mDownloaderLayer.downloadDataFromUri(mContext, uri))
        // Ignore empty response finally.
        if (response == AppUtils.EMPTY_STRING || response == "[]") {
            response = AppUtils.EMPTY_STRING
            AppLogger.w("$CLASS_NAME can not parse data, response is empty")
            return response
        }
        // Remove previous record.
        mApiCachePersistent.remove(responsesMapKey)
        mApiCacheInMemory.remove(responsesMapKey)
        // Finally, cache new response.
        mApiCachePersistent.put(responsesMapKey, response)
        mApiCacheInMemory.put(responsesMapKey, response)
        return response
    }

    companion object {
        /**
         * Tag string to use in logging messages.
         */
        private const val CLASS_NAME = "ASPI"
    }
}
