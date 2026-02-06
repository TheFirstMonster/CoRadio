package app.coradio.shared.model.media

import androidx.media3.common.MediaItem

interface MediaItemsSubscription {

    fun onChildrenLoaded(parentId: String, children: List<MediaItem>)

    fun onError(parentId: String)
}
