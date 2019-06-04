package circlet.components

import circlet.settings.*
import circlet.utils.*
import com.intellij.openapi.components.*
import runtime.reactive.*

val circletServerSettings get() = application.getComponent<CircletServerSettingsComponent>()

@State(
    name = "CircletServerConfigurable",
    storages = [Storage(value = "CircletServer.xml", roamingType = RoamingType.DEFAULT)]
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

}

@State(
    name = "CircletAutomationConfigurable",
    storages = [Storage(value = "CircletAutomation.xml", roamingType = RoamingType.DEFAULT)]
)
class CircletAutomationSettingsComponent : PersistentStateComponent<CircletAutomationSettings> {

    private val settings = mutableProperty(CircletAutomationSettings())

    override fun getState() = settings.value

    override fun loadState(state: CircletAutomationSettings) {
        settings.value = state
    }

    fun applySettings(state: CircletAutomationSettings) {
        settings.value = state
    }

}
