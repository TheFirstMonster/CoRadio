package app.coradio.automotive.ui

import app.coradio.shared.model.storage.LocationStorage

class AutomotiveSettingsActivityPresenterImpl(
    private val mLocationStorage: LocationStorage
) : AutomotiveSettingsActivityPresenter {

    override fun getCountryCode(): String {
        return mLocationStorage.getCountryCode()
    }
}
