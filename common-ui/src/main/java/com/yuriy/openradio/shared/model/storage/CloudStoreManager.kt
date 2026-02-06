/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package app.coradio.shared.model.storage

import android.app.Activity
import app.coradio.shared.dependencies.DependencyRegistryCommonUi
import app.coradio.shared.dependencies.StorageManagerDependency
import app.coradio.shared.utils.AppLogger

class CloudStoreManager : StorageManagerDependency {

    private lateinit var mStorageManagerLayer: StorageManagerLayer

    init {
        DependencyRegistryCommonUi.injectStorageManagerLayer(this)
    }

    override fun configureWith(storageManagerLayer: StorageManagerLayer) {
        mStorageManagerLayer = storageManagerLayer
    }

    fun isUserExist(): Boolean {
        return false
    }

    fun getUserEmail(): String {
        return "no email found"
    }

    fun signOut() {
        // Cloud storage disabled
    }

    fun deleteAccount(
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onInternalError: () -> Unit,
        onRecentLoginRequired: () -> Unit
    ) {
        onFailure()
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        onFailure()
    }

    fun getToken(
        onSuccess: (token: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        onFailure("Cloud storage disabled")
    }

    fun signIn(
        activity: Activity,
        email: String,
        password: String,
        onSuccess: (token: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        onFailure("Cloud storage disabled")
    }

    fun download(
        token: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        onFailure()
    }

    companion object {
        private const val TAG = "FSM"
    }
}
