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

package com.yuriy.openradio.shared.dependencies

import android.content.Context
import com.yuriy.openradio.shared.model.cast.CastLayer
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.logging.LoggingLayer
import com.yuriy.openradio.shared.model.logging.LoggingLayerImpl
import com.yuriy.openradio.shared.model.media.RadioStationManagerLayer
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.model.storage.CloudStoreManager
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.FileStoreManager
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.StorageManagerLayer
import com.yuriy.openradio.shared.model.storage.StorageManagerLayerImpl
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.presenter.MediaPresenter
import com.yuriy.openradio.shared.presenter.MediaPresenterImpl
import com.yuriy.openradio.shared.view.dialog.AddEditStationDialogPresenter
import com.yuriy.openradio.shared.view.dialog.AddEditStationDialogPresenterImpl
import com.yuriy.openradio.shared.view.dialog.BaseAddEditStationDialog
import com.yuriy.openradio.shared.view.dialog.EditStationDialog
import com.yuriy.openradio.shared.view.dialog.EditStationPresenter
import com.yuriy.openradio.shared.view.dialog.EditStationPresenterImpl
import com.yuriy.openradio.shared.view.dialog.EqualizerDialog
import com.yuriy.openradio.shared.view.dialog.EqualizerPresenter
import com.yuriy.openradio.shared.view.dialog.EqualizerPresenterImpl
import com.yuriy.openradio.shared.view.dialog.NetworkDialog
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialog
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialogPresenter
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialogPresenterImpl
import java.util.concurrent.atomic.AtomicBoolean

object DependencyRegistryCommonUi :
    NetworkLayerDependency, LocationStorageDependency, FavoritesStorageDependency,
    DeviceLocalsStorageDependency, EqualizerLayerDependency, RadioStationManagerLayerDependency,
    NetworkSettingsStorageDependency, SleepTimerModelDependency, SourcesLayerDependency, CastLayerDependency {

    private lateinit var sMediaPresenter: MediaPresenter
    private lateinit var sEditStationPresenter: EditStationPresenter
    private lateinit var sEqualizerPresenter: EqualizerPresenter
    private lateinit var sRemoveStationDialogPresenter: RemoveStationDialogPresenter
    private lateinit var sAddEditStationDialogPresenter: AddEditStationDialogPresenter
    private lateinit var sStorageManagerLayer: StorageManagerLayer
    private lateinit var sNetworkLayer: NetworkLayer
    private lateinit var sLocationStorage: LocationStorage
    private lateinit var sFavoritesStorage: FavoritesStorage
    private lateinit var sDeviceLocalsStorage: DeviceLocalsStorage
    private lateinit var sNetworkSettingsStorage: NetworkSettingsStorage
    private lateinit var sEqualizerLayer: EqualizerLayer
    private lateinit var sRadioStationManagerLayer: RadioStationManagerLayer
    private lateinit var sSleepTimerModel: SleepTimerModel
    private lateinit var sSourcesLayer: SourcesLayer
    private lateinit var sCastLayer: CastLayer
    private lateinit var sLoggingLayer: LoggingLayer
    private lateinit var sCloudStoreManager: CloudStoreManager
    private lateinit var sFileStoraManager: FileStoreManager

    @Volatile
    private var sInit = AtomicBoolean(false)

    /**
     * Init with application's context only!
     */
    fun init(context: Context) {
        if (sInit.get()) {
            return
        }
        DependencyRegistryCommon.injectNetworkLayer(this)
        DependencyRegistryCommon.injectLocationStorage(this)
        DependencyRegistryCommon.injectFavoritesStorage(this)
        DependencyRegistryCommon.injectDeviceLocalsStorage(this)
        DependencyRegistryCommon.injectEqualizerLayer(this)
        DependencyRegistryCommon.injectRadioStationManagerLayer(this)
        DependencyRegistryCommon.injectNetworkSettingsStorage(this)
        DependencyRegistryCommon.injectSleepTimerModel(this)
        DependencyRegistryCommon.injectSourcesLayer(this)
        DependencyRegistryCommon.injectCastLayer(this)
        sLoggingLayer = LoggingLayerImpl(context)
        sMediaPresenter = MediaPresenterImpl(
            context,
            sNetworkLayer,
            sLocationStorage,
            sSleepTimerModel,
            sSourcesLayer,
            sFavoritesStorage,
            sCastLayer
        )
        sEditStationPresenter = EditStationPresenterImpl(
            sFavoritesStorage,
            sDeviceLocalsStorage
        )
        sStorageManagerLayer = StorageManagerLayerImpl(
            sFavoritesStorage,
            sDeviceLocalsStorage
        )
        sEqualizerPresenter = EqualizerPresenterImpl(sEqualizerLayer)
        sRemoveStationDialogPresenter = RemoveStationDialogPresenterImpl(
            context,
            sRadioStationManagerLayer
        )
        sAddEditStationDialogPresenter = AddEditStationDialogPresenterImpl(
            context,
            sRadioStationManagerLayer
        )
        sCloudStoreManager = CloudStoreManager()
        sFileStoraManager = FileStoreManager()

        sInit.set(true)
    }

    override fun configureWith(castLayer: CastLayer) {
        sCastLayer = castLayer
    }

    override fun configureWith(sourcesLayer: SourcesLayer) {
        sSourcesLayer = sourcesLayer
    }

    override fun configureWith(networkLayer: NetworkLayer) {
        sNetworkLayer = networkLayer
    }

    override fun configureWith(locationStorage: LocationStorage) {
        sLocationStorage = locationStorage
    }

    override fun configureWith(favoritesStorage: FavoritesStorage) {
        sFavoritesStorage = favoritesStorage
    }

    override fun configureWith(deviceLocalsStorage: DeviceLocalsStorage) {
        sDeviceLocalsStorage = deviceLocalsStorage
    }

    override fun configureWith(equalizerLayer: EqualizerLayer) {
        sEqualizerLayer = equalizerLayer
    }

    override fun configureWith(radioStationManagerLayer: RadioStationManagerLayer) {
        sRadioStationManagerLayer = radioStationManagerLayer
    }

    override fun configureWith(networkSettingsStorage: NetworkSettingsStorage) {
        sNetworkSettingsStorage = networkSettingsStorage
    }

    override fun configureWith(sleepTimerModel: SleepTimerModel) {
        sSleepTimerModel = sleepTimerModel
    }

    fun inject(dependency: MediaPresenterDependency) {
        dependency.configureWith(sMediaPresenter)
    }

    fun injectStorageManagerLayer(dependency: StorageManagerDependency) {
        dependency.configureWith(sStorageManagerLayer)
    }

    fun injectCloudStoreManager(dependency: CloudStoreManagerDependency) {
        dependency.configureWith(sCloudStoreManager)
    }

    fun injectFileStoreManager(dependency: FileStoreManagerDependency) {
        dependency.configureWith(sFileStoraManager)
    }

    fun inject(dependency: EditStationDialog) {
        dependency.configureWith(sEditStationPresenter)
    }

    fun inject(dependency: EqualizerDialog) {
        dependency.configureWith(sEqualizerPresenter)
    }

    fun inject(dependency: RemoveStationDialog) {
        dependency.configureWith(sRemoveStationDialogPresenter)
    }

    fun inject(dependency: BaseAddEditStationDialog) {
        dependency.configureWith(sAddEditStationDialogPresenter)
    }

    fun inject(dependency: NetworkDialog) {
        dependency.configureWith(sNetworkSettingsStorage)
    }

    fun injectServiceCommander(dependency: ServiceCommanderDependency) {
        dependency.configureWith(sMediaPresenter.getServiceCommander())
    }

    fun injectLoggingLayer(dependency: LoggingLayerDependency) {
        dependency.configureWith(sLoggingLayer)
    }
}
