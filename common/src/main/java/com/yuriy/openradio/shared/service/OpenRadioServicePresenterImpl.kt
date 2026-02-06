package app.coradio.shared.service

import android.content.Context
import app.coradio.shared.model.ModelLayer
import app.coradio.shared.model.cast.CastLayer
import app.coradio.shared.model.eq.EqualizerLayer
import app.coradio.shared.model.media.Category
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.RadioStationManagerLayerListener
import app.coradio.shared.model.media.item.MediaItemAllCategories
import app.coradio.shared.model.media.item.MediaItemBrowseCar
import app.coradio.shared.model.media.item.MediaItemChildCategories
import app.coradio.shared.model.media.item.MediaItemCommand
import app.coradio.shared.model.media.item.MediaItemCountriesList
import app.coradio.shared.model.media.item.MediaItemCountryStations
import app.coradio.shared.model.media.item.MediaItemFavoritesList
import app.coradio.shared.model.media.item.MediaItemFeatured
import app.coradio.shared.model.media.item.MediaItemLocalsList
import app.coradio.shared.model.media.item.MediaItemNewStations
import app.coradio.shared.model.media.item.MediaItemPopularStations
import app.coradio.shared.model.media.item.MediaItemRoot
import app.coradio.shared.model.media.item.MediaItemRootCar
import app.coradio.shared.model.media.item.MediaItemSearchFromApp
import app.coradio.shared.model.media.item.MediaItemSearchFromService
import app.coradio.shared.model.net.NetworkLayer
import app.coradio.shared.model.net.NetworkMonitorListener
import app.coradio.shared.model.net.UrlLayer
import app.coradio.shared.model.source.Source
import app.coradio.shared.model.storage.DeviceLocalsStorage
import app.coradio.shared.model.storage.FavoritesStorage
import app.coradio.shared.model.storage.LatestRadioStationStorage
import app.coradio.shared.model.storage.LocationStorage
import app.coradio.shared.model.storage.NetworkSettingsStorage
import app.coradio.shared.model.storage.cache.api.ApiCache
import app.coradio.shared.model.storage.images.ImagesPersistenceLayer
import app.coradio.shared.model.timer.SleepTimerModel
import app.coradio.shared.model.translation.MediaIdBuilder
import app.coradio.shared.model.translation.MediaIdBuilderDefault
import app.coradio.shared.service.location.Country
import app.coradio.shared.utils.SortUtils
import java.util.TreeSet

class CoRadioServicePresenterImpl(
    isCar: Boolean,
    source: Source,
    private val mUrlLayer: UrlLayer,
    private val mNetworkLayer: NetworkLayer,
    private val mModelLayer: ModelLayer,
    private var mFavoritesStorage: FavoritesStorage,
    private val mDeviceLocalsStorage: DeviceLocalsStorage,
    private val mLatestRadioStationStorage: LatestRadioStationStorage,
    private val mNetworkSettingsStorage: NetworkSettingsStorage,
    private val mLocationStorage: LocationStorage,
    private var mImagesPersistenceLayer: ImagesPersistenceLayer,
    private val mEqualizerLayer: EqualizerLayer,
    private val mApiCachePersistent: ApiCache,
    private val mApiCacheInMemory: ApiCache,
    private val mSleepTimerModel: SleepTimerModel,
    private val mCountriesCache:TreeSet<Country>,
    private val mListener: RadioStationManagerLayerListener,
    private val mCastLayer: CastLayer
) : CoRadioServicePresenter {

    /**
     * Map of the Media Item commands that responsible for the Media Items List creation.
     */
    private val mMediaItemCommands = HashMap<String, MediaItemCommand>()

    init {
        if (isCar) {
            mMediaItemCommands[MediaId.MEDIA_ID_ROOT] = MediaItemRootCar(source)
            mMediaItemCommands[MediaId.MEDIA_ID_BROWSE_CAR] = MediaItemBrowseCar(source)
        } else {
            mMediaItemCommands[MediaId.MEDIA_ID_ROOT] = MediaItemRoot(source)
        }
        mMediaItemCommands[MediaId.MEDIA_ID_ALL_CATEGORIES] = MediaItemAllCategories()
        mMediaItemCommands[MediaId.MEDIA_ID_COUNTRIES_LIST] = MediaItemCountriesList()
        mMediaItemCommands[MediaId.MEDIA_ID_COUNTRY_STATIONS] = MediaItemCountryStations()
        mMediaItemCommands[MediaId.MEDIA_ID_CHILD_CATEGORIES] = MediaItemChildCategories()
        mMediaItemCommands[MediaId.MEDIA_ID_FAVORITES_LIST] = MediaItemFavoritesList()
        mMediaItemCommands[MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST] = MediaItemLocalsList()
        mMediaItemCommands[MediaId.MEDIA_ID_SEARCH_FROM_APP] = MediaItemSearchFromApp()
        mMediaItemCommands[MediaId.MEDIA_ID_SEARCH_FROM_SERVICE] = MediaItemSearchFromService()
        mMediaItemCommands[MediaId.MEDIA_ID_POPULAR_STATIONS] = MediaItemPopularStations()
        mMediaItemCommands[MediaId.MEDIA_ID_NEW_STATIONS] = MediaItemNewStations()
        mMediaItemCommands[MediaId.MEDIA_ID_FEATURED_LIST] = MediaItemFeatured()
    }

    override fun getMediaItemCommand(commandId: String): MediaItemCommand? {
        return mMediaItemCommands[commandId]
    }

    override fun getCastLayer(): CastLayer {
        return mCastLayer
    }

    override fun startNetworkMonitor(context: Context, listener: NetworkMonitorListener) {
        mNetworkLayer.startMonitor(context, listener)
    }

    override fun stopNetworkMonitor(context: Context) {
        mNetworkLayer.stopMonitor(context)
    }

    override fun isMobileNetwork(): Boolean {
        return mNetworkLayer.isMobileNetwork()
    }

    override fun getUseMobile(): Boolean {
        return mNetworkSettingsStorage.getUseMobile()
    }

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getStationsInCategory(
                categoryId,
                pageNumber
            ),
            MediaIdBuilderDefault()
        )
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getStationsByCountry(
                countryCode,
                pageNumber
            ),
            MediaIdBuilderDefault()
        )
    }

    override fun getNewStations(): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getNewStations(),
            MediaIdBuilderDefault()
        )
    }

    override fun getPopularStations(): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getPopularStations(),
            MediaIdBuilderDefault()
        )
    }

    override fun getSearchStations(query: String, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getSearchUrl(query), mediaIdBuilder
        )
    }

    override fun getFeatured(): Set<RadioStation> {
        return mModelLayer.getFeatured()
    }

    override fun getAllCategories(): Set<Category> {
        return mModelLayer.getAllCategories(mUrlLayer.getAllCategoriesUrl())
    }

    @Synchronized
    override fun getAllCountries(): Set<Country> {
        if (mCountriesCache.isEmpty().not()) {
            return mCountriesCache
        }
        mCountriesCache.addAll(mModelLayer.getAllCountries(mUrlLayer.getAllCountries()))
        return mCountriesCache
    }

    override fun getAllFavorites(): Set<RadioStation> {
        return mFavoritesStorage.getAll()
    }

    override fun getAllDeviceLocal(): Set<RadioStation> {
        return mDeviceLocalsStorage.getAll()
    }

    override fun getLastRadioStation(): RadioStation {
        return mLatestRadioStationStorage.get()
    }

    override fun setLastRadioStation(radioStation: RadioStation) {
        mLatestRadioStationStorage.add(radioStation)
    }

    override fun getCountryCode(): String {
        return mLocationStorage.getCountryCode()
    }

    override fun isRadioStationFavorite(radioStation: RadioStation): Boolean {
        return mFavoritesStorage.isFavorite(radioStation)
    }

    override fun updateRadioStationFavorite(radioStation: RadioStation) {
        updateRadioStationFavorite(radioStation, isRadioStationFavorite(radioStation).not())
    }

    override fun updateRadioStationFavorite(radioStation: RadioStation, isFavorite: Boolean) {
        if (isFavorite) {
            mFavoritesStorage.add(radioStation)
        } else {
            mFavoritesStorage.remove(radioStation)
        }
    }

    override fun updateSortIds(
        mediaId: String,
        sortId: Int,
        categoryMediaId: String
    ) {
        SortUtils.updateSortIds(
            mediaId, sortId, categoryMediaId,
            mFavoritesStorage, mDeviceLocalsStorage
        )
    }

    override fun getEqualizerLayer(): EqualizerLayer {
        return mEqualizerLayer
    }

    override fun clear() {
        mApiCachePersistent.clear()
        mApiCacheInMemory.clear()
        mImagesPersistenceLayer.deleteAll()
        mLatestRadioStationStorage.clear()
    }

    override fun close() {
        mApiCacheInMemory.clear()
    }

    override fun getSleepTimerModel(): SleepTimerModel {
        return mSleepTimerModel
    }
}
