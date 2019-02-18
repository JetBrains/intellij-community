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

    suspend fun connect(refreshToken: String?, state: CircletServerSettings) {
        if (refreshToken != null) {
            IdeaPasswordSafePersistence.put("refresh_token", refreshToken)
        } else {
            IdeaPasswordSafePersistence.put("refresh_token", "")
        }
        settings.value = state
    }

}
