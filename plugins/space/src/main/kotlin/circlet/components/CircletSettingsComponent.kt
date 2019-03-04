package circlet.components

import circlet.settings.*
import circlet.utils.*
import com.intellij.openapi.components.*
import runtime.reactive.*

val circletSettings get() = application.getComponent<CircletSettingsComponent>()

@State(
    name = "CircletConfigurable",
    storages = [Storage(value = "Circlet.xml", roamingType = RoamingType.DEFAULT)]
)
class CircletSettingsComponent : PersistentStateComponent<CircletServerSettings> {

    val settings = mutableProperty(CircletServerSettings())

    override fun getState() = settings.value

    override fun loadState(state: CircletServerSettings) {
        settings.value = state
    }

    fun applySettings(state: CircletServerSettings) {
        settings.value = state
    }

}
