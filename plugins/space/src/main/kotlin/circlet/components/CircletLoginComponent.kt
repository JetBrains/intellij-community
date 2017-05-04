package circlet.components

import circlet.utils.*
import com.intellij.openapi.components.*
import klogging.*
import runtime.reactive.*

private val log = KLoggers.logger("app-idea/CircletLoginComponent.kt")

data class IdePLuginClientData(
    var myEnabled: Boolean? = null
)

@State(
    name = "CircletLoginComponent",
    storages = arrayOf(Storage(
        value = "CircletClient.xml",
        roamingType = RoamingType.DISABLED)))
class CircletLoginComponent() :
    ILifetimedApplicationComponent by LifetimedApplicationComponent(),
    PersistentStateComponent<IdePLuginClientData> {

    val credentialsUpdated = Signal.create<Boolean>()

    val enabled = Property.createMutable(false)

    override fun loadState(state: IdePLuginClientData) {
        enabled.value = state.myEnabled ?: false
    }

    override fun getState(): IdePLuginClientData =
        IdePLuginClientData(enabled.value)

}
