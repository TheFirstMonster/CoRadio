/*
 * Copyright 2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.service

import android.os.Bundle
import app.coradio.shared.model.media.MediaId
import app.coradio.shared.utils.AppUtils

/**
 * [CoRadioStore] is the object that provides ability to perform one way communication with [CoRadioService].
 */
object CoRadioStore {

    const val EXTRA_KEY_MEDIA_ID = "EXTRA_KEY_MEDIA_ID"
    const val EXTRA_KEY_MEDIA_IDS = "EXTRA_KEY_MEDIA_IDS"
    const val EXTRA_KEY_SORT_IDS = "EXTRA_KEY_SORT_IDS"
    const val EXTRA_KEY_MASTER_VOLUME = "EXTRA_KEY_MASTER_VOLUME"

    private const val BUNDLE_ARG_CATALOGUE_ID = "BUNDLE_ARG_CATALOGUE_ID"
    private const val BUNDLE_ARG_IS_RESTORE_STATE = "BUNDLE_ARG_IS_RESTORE_STATE"

    fun makeMasterVolumeChangedBundle(masterVolume: Int): Bundle {
        val intent = Bundle()
        intent.putInt(EXTRA_KEY_MASTER_VOLUME, masterVolume)
        return intent
    }

    /**
     * Factory method to make [Bundle] to update Sort Ids of the Radio Stations.
     *
     * @param mediaId               Array of the Media Ids (of the Radio Stations).
     * @param sortId                Array of the corresponded Sort Ids.
     * @param parentCategoryMediaId ID of the current category ([etc ...][MediaId.MEDIA_ID_FAVORITES_LIST]).
     * @return [Bundle].
     */
    fun makeUpdateSortIdsBundle(mediaId: String, sortId: Int, parentCategoryMediaId: String): Bundle {
        val bundle = Bundle()
        bundle.putString(EXTRA_KEY_MEDIA_IDS, mediaId)
        bundle.putInt(EXTRA_KEY_SORT_IDS, sortId)
        bundle.putString(EXTRA_KEY_MEDIA_ID, parentCategoryMediaId)
        return bundle
    }

    /**
     * Factory method to make [Bundle] to update whether [app.coradio.shared.model.media.RadioStation]
     * is Favorite.
     *
     * @param mediaId Media Id of the [app.coradio.shared.model.media.RadioStation].
     * @param isFavorite Whether Radio station is Favorite or not.
     * @return [Bundle].
     */
    fun makeUpdateIsFavoriteBundle(mediaId: String): Bundle {
        val bundle = Bundle()
        bundle.putString(EXTRA_KEY_MEDIA_ID, mediaId)
        return bundle
    }

    fun extractMediaId(bundle: Bundle?): String {
        return bundle?.getString(EXTRA_KEY_MEDIA_ID) ?: AppUtils.EMPTY_STRING
    }

    fun putCurrentParentId(bundle: Bundle?, currentParentId: String?) {
        if (bundle == null) {
            return
        }
        bundle.putString(BUNDLE_ARG_CATALOGUE_ID, currentParentId)
    }

    fun getCurrentParentId(bundle: Bundle?): String {
        return if (bundle == null) {
            AppUtils.EMPTY_STRING
        } else bundle.getString(BUNDLE_ARG_CATALOGUE_ID, AppUtils.EMPTY_STRING)
    }

    fun putRestoreState(bundle: Bundle?, value: Boolean) {
        if (bundle == null) {
            return
        }
        bundle.putBoolean(BUNDLE_ARG_IS_RESTORE_STATE, value)
    }

    fun getRestoreState(bundle: Bundle?): Boolean {
        return bundle?.getBoolean(BUNDLE_ARG_IS_RESTORE_STATE, false) ?: false
    }
}
