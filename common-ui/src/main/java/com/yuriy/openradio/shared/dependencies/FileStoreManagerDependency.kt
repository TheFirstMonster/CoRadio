/*
 * Copyright 2024 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import app.coradio.shared.model.storage.FileStoreManager

/**
 * [FileStoreManagerDependency] is an interface that helps to inject [FileStoreManager] into a client.
 */
interface FileStoreManagerDependency {

    /**
     * Callback to provide [FileStoreManager] to a client.
     */
    fun configureWith(fileStoreManager: FileStoreManager)
}
