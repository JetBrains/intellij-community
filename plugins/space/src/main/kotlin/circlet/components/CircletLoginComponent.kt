package circlet.components

import circlet.client.*
import circlet.utils.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import klogging.*
import runtime.async.*
import runtime.reactive.*

private val log = KLoggers.logger("app-idea/CircletLoginComponent.kt")

data class IdePLuginClientData(
    var enabled: Boolean? = null
)

@State(name = "CircletLoginComponent",
       storages = arrayOf(Storage(value = "CircletClient.xml", roamingType = RoamingType.DISABLED)))
class CircletLoginComponent :
    ILifetimedApplicationComponent by LifetimedApplicationComponent(),
    PersistentStateComponent<IdePLuginClientData> {

    val enabled = Property.createMutable(false)

    override fun loadState(state: IdePLuginClientData) {
        enabled.value = state.enabled ?: false
    }

    override fun getState(): IdePLuginClientData =
        IdePLuginClientData(enabled = enabled.value)

}
