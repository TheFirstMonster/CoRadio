package app.coradio.shared.view.dialog

import app.coradio.shared.model.media.RadioStation

/**
 * // TODO : Move business logic from Edit Dialog here.
 */
interface EditStationPresenter {

    fun getDeviceLocalRadioStation(mediaId: String): RadioStation

    fun isRadioStationFavorite(radioStation: RadioStation): Boolean
}
