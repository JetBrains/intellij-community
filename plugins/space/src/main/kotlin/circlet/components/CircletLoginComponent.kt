package circlet.components

import circlet.utils.*
import com.intellij.openapi.components.*
import klogging.*
import runtime.reactive.*

@Suppress("unused")
private val log = KLoggers.logger("app-idea/CircletLoginComponent.kt")

data class IdeaPluginClientData(
    var enabled: Boolean? = null
)

@State(
    name = "CircletLoginComponent",
    storages = [Storage(value = "CircletClient.xml", roamingType = RoamingType.DISABLED)]
)
class CircletLoginComponent :
    ILifetimedApplicationComponent by LifetimedApplicationComponent(),
    PersistentStateComponent<IdeaPluginClientData> {

    val enabled = Property.createMutable(false)

    override fun loadState(state: IdeaPluginClientData) {
        enabled.value = state.enabled ?: false
    }

    override fun getState(): IdeaPluginClientData =
        IdeaPluginClientData(enabled = enabled.value)
}
