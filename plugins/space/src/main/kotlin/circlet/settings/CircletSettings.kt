package circlet.settings

import com.intellij.openapi.components.*
import runtime.reactive.*

@State(
    name = "SpaceServerConfigurable",
    storages = [Storage(value = "SpaceServer.xml", roamingType = RoamingType.DEFAULT)]
)
class CircletServerSettingsComponent : PersistentStateComponent<CircletServerSettings> {

    val settings = mutableProperty(CircletServerSettings())

    override fun getState() = settings.value

    override fun loadState(state: CircletServerSettings) {
        settings.value = state
    }

    fun applySettings(state: CircletServerSettings) {
        settings.value = state
    }

    companion object {
        fun getInstance(): CircletServerSettingsComponent = ServiceManager.getService(CircletServerSettingsComponent::class.java)
    }

}
