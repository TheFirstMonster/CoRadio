package app.coradio.shared.model.media

import android.content.Context

interface RadioStationManagerLayer {

    fun addRadioStation(
        context: Context, rsToAdd: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    )

    fun editRadioStation(
        context: Context, mediaId: String, rsToAdd: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    )

    fun removeRadioStation(context: Context?, mediaId: String?)
}
