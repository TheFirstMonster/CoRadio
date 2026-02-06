package app.coradio.shared.view.dialog

import android.content.Context
import app.coradio.shared.model.media.RadioStationManagerLayer
import app.coradio.shared.model.media.RadioStationToAdd

class AddEditStationDialogPresenterImpl(
    private val mContext: Context,
    private val mRadioStationManagerLayer: RadioStationManagerLayer
) : AddEditStationDialogPresenter {

    override fun addRadioStation(
        radioStation: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        mRadioStationManagerLayer.addRadioStation(mContext, radioStation, onSuccess, onFailure)
    }

    override fun editRadioStation(
        mediaId: String,
        radioStation: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        mRadioStationManagerLayer.editRadioStation(mContext, mediaId, radioStation, onSuccess, onFailure)
    }
}
