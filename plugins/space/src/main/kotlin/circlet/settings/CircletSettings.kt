package circlet.settings

import com.intellij.openapi.components.*

@State(
    name = "SpaceServerConfigurable",
    storages = [Storage(value = "SpaceServer.xml", roamingType = RoamingType.DEFAULT)]
)
class CircletSettings : SimplePersistentStateComponent<CircletSettingsState>(CircletSettingsState()) {
    var serverSettings: CircletServerSettings
        get() = state.serverSettings
        set(value) {
            state.serverSettings = value
        }

    var cloneType: CloneType
        get() = state.cloneType
        set(value) {
            state.cloneType = value
        }

    companion object {
        fun getInstance(): CircletSettings = ServiceManager.getService(CircletSettings::class.java)
    }
}

class CircletSettingsState : BaseState() {
    var serverSettings: CircletServerSettings by this.property(CircletServerSettings()) {
        it.enabled.not() && it.server.isBlank()
    }

    var cloneType: CloneType by enum(CloneType.HTTP)
}

enum class CloneType {
    HTTP,
    SSH
}
