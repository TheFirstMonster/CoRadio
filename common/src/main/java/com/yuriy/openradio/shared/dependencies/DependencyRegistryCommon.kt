/*
 * Copyright 2021-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.dependencies

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import androidx.multidex.MultiDexApplication
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import app.coradio.shared.model.ModelLayerImpl
import app.coradio.shared.model.cast.CastLayer
import app.coradio.shared.model.cast.CastLayerImpl
import app.coradio.shared.model.eq.EqualizerLayer
import app.coradio.shared.model.eq.EqualizerLayerImpl
import app.coradio.shared.model.filter.FilterImpl
import app.coradio.shared.model.media.RadioStationManagerLayer
import app.coradio.shared.model.media.RadioStationManagerLayerImpl
import app.coradio.shared.model.media.RadioStationManagerLayerListener
import app.coradio.shared.model.net.HTTPDownloaderImpl
import app.coradio.shared.model.net.NetworkLayer
import app.coradio.shared.model.net.NetworkLayerImpl
import app.coradio.shared.model.net.UrlLayer
import app.coradio.shared.model.net.UrlLayerRadioBrowserImpl
import app.coradio.shared.model.net.UrlLayerWebRadioImpl
import app.coradio.shared.model.parser.FeaturedParserLayer
import app.coradio.shared.model.parser.FeaturedParserLayerFirestone
import app.coradio.shared.model.parser.ParserLayer
import app.coradio.shared.model.parser.ParserLayerRadioBrowserImpl
import app.coradio.shared.model.parser.ParserLayerWebRadioImpl
import app.coradio.shared.model.source.Source
import app.coradio.shared.model.source.SourcesLayer
import app.coradio.shared.model.source.SourcesLayerImpl
import app.coradio.shared.model.storage.DeviceLocalsStorage
import app.coradio.shared.model.storage.EqualizerStorage
import app.coradio.shared.model.storage.FavoritesStorage
import app.coradio.shared.model.storage.LatestRadioStationStorage
import app.coradio.shared.model.storage.LocationStorage
import app.coradio.shared.model.storage.NetworkSettingsStorage
import app.coradio.shared.model.storage.cache.api.InMemoryApiCache
import app.coradio.shared.model.storage.cache.api.PersistentApiCache
import app.coradio.shared.model.storage.cache.api.PersistentApiDb
import app.coradio.shared.model.storage.images.ImagesDatabase
import app.coradio.shared.model.storage.images.ImagesPersistenceLayer
import app.coradio.shared.model.storage.images.ImagesPersistenceLayerImpl
import app.coradio.shared.model.storage.images.ImagesProvider
import app.coradio.shared.model.timer.SleepTimerModel
import app.coradio.shared.model.timer.SleepTimerModelImpl
import app.coradio.shared.service.CoRadioService
import app.coradio.shared.service.CoRadioServicePresenterImpl
import app.coradio.shared.service.location.Country
import app.coradio.shared.utils.AppLogger
import java.lang.ref.WeakReference
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicBoolean

object DependencyRegistryCommon {

    const val PAGE_SIZE = 250
    const val UNKNOWN_ID = -1

    private lateinit var sFavoritesStorage: FavoritesStorage
    private lateinit var sDeviceLocalsStorage: DeviceLocalsStorage
    private lateinit var sLatestRadioStationStorage: LatestRadioStationStorage
    private lateinit var sLocationStorage: LocationStorage
    private lateinit var sNetworkSettingsStorage: NetworkSettingsStorage
    private lateinit var sEqualizerLayer: EqualizerLayer
    private lateinit var sNetworkLayer: NetworkLayer
    private lateinit var sRadioStationManagerLayer: RadioStationManagerLayer
    private lateinit var sImagesPersistenceLayer: ImagesPersistenceLayer
    private lateinit var sCoRadioServicePresenter: CoRadioServicePresenterImpl
    private lateinit var sSleepTimerModel: SleepTimerModel
    private lateinit var sSourcesLayer: SourcesLayer
    private lateinit var sCastLayer: CastLayer

    /**
     * Flag that indicates whether application runs over normal Android or Android TV.
     */
    private var sIsTv = AtomicBoolean(false)
    private var sIsCar = AtomicBoolean(false)
    private var sIsGoogleApiAvailable = AtomicBoolean(false)

    @Volatile
    private var sInit = AtomicBoolean(false)

    fun init(context: Context) {
        if (sInit.get()) {
            return
        }
        AppLogger.i("DI common inited")
        val orientationStr: String
        val orientation = context.resources.configuration.orientation
        orientationStr = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "Landscape"
        } else {
            "Portrait"
        }
        val uiModeManager = context.getSystemService(MultiDexApplication.UI_MODE_SERVICE) as UiModeManager
        AppLogger.d("CurrentModeType:${uiModeManager.currentModeType}")
        isTv = if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            AppLogger.d("Running on TV Device in $orientationStr")
            true
        } else {
            AppLogger.d("Running on non-TV Device")
            false
        }
        isCar = if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
            AppLogger.d("Running on Car Device in $orientationStr")
            true
        } else {
            AppLogger.d("Running on non-Car Device")
            false
        }

        val connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        isGoogleApiAvailable = connectionResult == ConnectionResult.SUCCESS
        AppLogger.i("Google API:$connectionResult")

        sCastLayer = CastLayerImpl(context)
        sSourcesLayer = SourcesLayerImpl(context)
        sNetworkLayer = NetworkLayerImpl(
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        )
        val countriesCache = TreeSet<Country>()
        val source = sSourcesLayer.getActiveSource()
        val parser = getParserLayer(source, countriesCache)
        val featuredParser = FeaturedParserLayerFirestone()
        val urlLayer = getUrlLayer(source)
        val downloader = HTTPDownloaderImpl(urlLayer)
        val apiCachePersistent = PersistentApiCache(context, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
        val apiCacheInMemory = InMemoryApiCache()
        val modelLayer = ModelLayerImpl(
            context, parser, featuredParser, sNetworkLayer, downloader, apiCachePersistent, apiCacheInMemory
        )
        val contextRef = WeakReference(context)
        sFavoritesStorage = FavoritesStorage(contextRef)
        sLatestRadioStationStorage = LatestRadioStationStorage(contextRef)
        sDeviceLocalsStorage = DeviceLocalsStorage(
            contextRef, sFavoritesStorage, sLatestRadioStationStorage
        )
        val equalizerStorage = EqualizerStorage(contextRef)
        sEqualizerLayer = EqualizerLayerImpl(equalizerStorage)
        val imagesDatabase = ImagesDatabase.getInstance(context)
        sImagesPersistenceLayer = ImagesPersistenceLayerImpl(context, downloader, imagesDatabase)

        val listenerProxy = RadioStationManagerLayerListenerImpl()
        sRadioStationManagerLayer = RadioStationManagerLayerImpl(
            modelLayer, urlLayer, sDeviceLocalsStorage, sFavoritesStorage, sImagesPersistenceLayer, listenerProxy
        )
        sLocationStorage = LocationStorage(contextRef)
        sNetworkSettingsStorage = NetworkSettingsStorage(contextRef)
        sSleepTimerModel = SleepTimerModelImpl(contextRef)
        sCoRadioServicePresenter = CoRadioServicePresenterImpl(
            isCar,
            source,
            urlLayer,
            sNetworkLayer,
            modelLayer,
            sFavoritesStorage,
            sDeviceLocalsStorage,
            sLatestRadioStationStorage,
            sNetworkSettingsStorage,
            sLocationStorage,
            sImagesPersistenceLayer,
            sEqualizerLayer,
            apiCachePersistent,
            apiCacheInMemory,
            sSleepTimerModel,
            countriesCache,
            listenerProxy,
            sCastLayer
        )

        sInit.set(true)
    }

    var isGoogleApiAvailable: Boolean
        get() = sIsGoogleApiAvailable.get()
        set(value) {
            sIsGoogleApiAvailable.set(value)
        }

    var isTv: Boolean
        get() = sIsTv.get()
        set(value) {
            sIsTv.set(value)
        }

    var isCar: Boolean
        get() = sIsCar.get()
        set(value) {
            sIsCar.set(value)
        }

    fun inject(dependency: ImagesProvider) {
        dependency.configureWith(sImagesPersistenceLayer)
    }

    fun inject(service: CoRadioService) {
        service.configureWith(sCoRadioServicePresenter)
    }

    fun injectSleepTimerModel(dependency: SleepTimerModelDependency) {
        dependency.configureWith(sSleepTimerModel)
    }

    fun injectSourcesLayer(dependency: SourcesLayerDependency) {
        dependency.configureWith(sSourcesLayer)
    }
    fun injectCastLayer(dependency: CastLayerDependency) {
        dependency.configureWith(sCastLayer)
    }

    fun injectNetworkLayer(dependency: NetworkLayerDependency) {
        dependency.configureWith(sNetworkLayer)
    }

    fun injectLocationStorage(dependency: LocationStorageDependency) {
        dependency.configureWith(sLocationStorage)
    }

    fun injectFavoritesStorage(dependency: FavoritesStorageDependency) {
        dependency.configureWith(sFavoritesStorage)
    }

    fun injectDeviceLocalsStorage(dependency: DeviceLocalsStorageDependency) {
        dependency.configureWith(sDeviceLocalsStorage)
    }

    fun injectLatestRadioStationStorage(dependency: LatestRadioStationStorageDependency) {
        dependency.configureWith(sLatestRadioStationStorage)
    }

    fun injectEqualizerLayer(dependency: EqualizerLayerDependency) {
        dependency.configureWith(sEqualizerLayer)
    }

    fun injectRadioStationManagerLayer(dependency: RadioStationManagerLayerDependency) {
        dependency.configureWith(sRadioStationManagerLayer)
    }

    fun injectNetworkSettingsStorage(dependency: NetworkSettingsStorageDependency) {
        dependency.configureWith(sNetworkSettingsStorage)
    }

    private fun getUrlLayer(source: Source): UrlLayer {
        return if (source == Source.RADIO_BROWSER) {
            UrlLayerRadioBrowserImpl()
        } else {
            UrlLayerWebRadioImpl()
        }
    }

    private fun getParserLayer(source: Source, set: Set<Country>): ParserLayer {
        return if (source == Source.RADIO_BROWSER) {
            ParserLayerRadioBrowserImpl(FilterImpl())
        } else {
            ParserLayerWebRadioImpl(set)
        }
    }

    private class RadioStationManagerLayerListenerImpl : RadioStationManagerLayerListener {

        override fun notifyChildrenChangedBundle(parentId: String) {

        }

        override fun removeByMediaIdBundle(mediaId: String) {

        }
    }
}
