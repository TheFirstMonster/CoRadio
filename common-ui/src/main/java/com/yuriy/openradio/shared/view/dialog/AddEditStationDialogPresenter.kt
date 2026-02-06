package app.coradio.shared.view.dialog

import app.coradio.shared.model.media.RadioStationToAdd

interface AddEditStationDialogPresenter {

    fun addRadioStation(
        radioStation: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    )

    fun editRadioStation(
        mediaId: String, radioStation: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    )
}
