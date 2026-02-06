/*
 * Copyright 2020-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.service.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import app.coradio.shared.utils.AnalyticsUtils
import app.coradio.shared.utils.AppUtils
import app.coradio.shared.utils.AppUtils.getUserAgent
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

object ExoPlayerUtils {

    const val METADATA_ID_TT2 = "TT2"
    const val METADATA_ID_TIT2 = "TIT2"

    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
    private var sDataSourceFactory: DataSource.Factory? = null
    private var sHttpDataSourceFactory: HttpDataSource.Factory? = null
    private var sDownloadCache: Cache? = null
    private var sDownloadDirectory: File? = null
    private var sDatabaseProvider: DatabaseProvider? = null
    private var sUserAgent = AppUtils.EMPTY_STRING

    @UnstableApi
    fun buildRenderersFactory(context: Context): RenderersFactory {
        return DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    }

    /**
     * Returns a [DataSource.Factory].
     */
    @UnstableApi
    @Synchronized
    fun getDataSourceFactory(context: Context): DataSource.Factory? {
        val userAgent = getUserAgent(context)
        if (sUserAgent == userAgent) {
            sDataSourceFactory = null
            sHttpDataSourceFactory = null
        }
        sUserAgent = userAgent
        if (sDataSourceFactory == null) {
            val factory = getHttpDataSourceFactory(sUserAgent)
            val upstreamFactory = DefaultDataSource.Factory(context, factory!!)
            sDataSourceFactory = buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context)!!)
        }
        return sDataSourceFactory
    }

    @UnstableApi
    @Synchronized
    fun getHttpDataSourceFactory(userAgent: String): HttpDataSource.Factory? {
        if (sHttpDataSourceFactory == null) {
            AnalyticsUtils.logMessage("ExoPlayer UserAgent '$userAgent'")
            val cookieManager = CookieManager()
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
            CookieHandler.setDefault(cookieManager)
            sHttpDataSourceFactory =
                DefaultHttpDataSource.Factory().setUserAgent(userAgent).setAllowCrossProtocolRedirects(true)
        }
        return sHttpDataSourceFactory
    }

    @UnstableApi
    private fun buildReadOnlyCacheDataSource(
        upstreamFactory: DataSource.Factory,
        cache: Cache
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @UnstableApi
    @Synchronized
    private fun getDownloadCache(context: Context): Cache? {
        if (sDownloadCache == null) {
            val downloadContentDirectory = File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY)
            sDownloadCache = SimpleCache(
                downloadContentDirectory, NoOpCacheEvictor(), getDatabaseProvider(context)!!
            )
        }
        return sDownloadCache
    }

    @Synchronized
    private fun getDownloadDirectory(context: Context): File? {
        if (sDownloadDirectory == null) {
            sDownloadDirectory = context.getExternalFilesDir(null)
            if (sDownloadDirectory == null) {
                sDownloadDirectory = context.filesDir
            }
        }
        return sDownloadDirectory
    }

    @UnstableApi
    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider? {
        if (sDatabaseProvider == null) {
            sDatabaseProvider = StandaloneDatabaseProvider(context)
        }
        return sDatabaseProvider
    }
}
