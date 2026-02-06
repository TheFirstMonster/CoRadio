package app.coradio.automotive.dependencies

import app.coradio.automotive.ui.AutomotiveSettingsActivity
import app.coradio.automotive.ui.AutomotiveSettingsActivityPresenter
import app.coradio.automotive.ui.AutomotiveSettingsActivityPresenterImpl
import app.coradio.shared.dependencies.DependencyRegistryCommon
import app.coradio.shared.dependencies.LocationStorageDependency
import app.coradio.shared.model.storage.LocationStorage
import java.util.concurrent.atomic.AtomicBoolean

object DependencyRegistryAutomotive : LocationStorageDependency {

    private lateinit var sAutomotiveSettingsActivityPresenter: AutomotiveSettingsActivityPresenter
    private lateinit var sLocationStorage: LocationStorage

    @Volatile
    private var sInit = AtomicBoolean(false)

    /**
     * Init with application's context only!
     */
    fun init() {
        if (sInit.get()) {
            return
        }

        DependencyRegistryCommon.injectLocationStorage(this)
        sAutomotiveSettingsActivityPresenter = AutomotiveSettingsActivityPresenterImpl(
            sLocationStorage
        )

        sInit.set(true)
    }

    override fun configureWith(locationStorage: LocationStorage) {
        sLocationStorage = locationStorage
    }

    fun inject(dependency: AutomotiveSettingsActivity) {
        dependency.configureWith(sAutomotiveSettingsActivityPresenter)
    }
}
