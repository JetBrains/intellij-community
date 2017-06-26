package circlet.components

import circlet.client.*
import circlet.utils.*
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

    val endpoint = "http://latest.n.circlet.labs.intellij.net"
    val loginModel = LoginModel(IdeaPersistence, "localhost:8080")

    val enabled = Property.createMutable(false)
    val token = mutableProperty<Int>(0)

    override fun loadState(state: IdePLuginClientData) {
        enabled.value = state.enabled ?: false
    }

    fun setToken(tk : String) {
        async {
            IdeaPersistence.put("token", tk)
            token.value++
        }
    }

    override fun getState(): IdePLuginClientData =
        IdePLuginClientData(enabled = enabled.value)

}
