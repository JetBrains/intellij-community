package circlet.components

import circlet.utils.*
import com.intellij.openapi.components.*
import klogging.*
import runtime.reactive.*

private val log = KLoggers.logger("app-idea/CircletLoginComponent.kt")

data class IdePLuginClientData(
    var enabled: Boolean? = null,
    var orgName : String? = null,
    var login : String? = null,
    var url : String? = null
)

@State(
    name = "CircletLoginComponent",
    storages = arrayOf(Storage(
        value = "CircletClient.xml",
        roamingType = RoamingType.DISABLED)))
class CircletLoginComponent() :
    ILifetimedApplicationComponent by LifetimedApplicationComponent(),
    PersistentStateComponent<IdePLuginClientData> {

    val enabled = Property.createMutable(false)
    val orgName = Property.createMutable("")
    val url = Property.createMutable("")
    val token = mutableProperty<Int>(0)
    val login = Property.createMutable("")

    override fun loadState(state: IdePLuginClientData) {
        enabled.value = state.enabled ?: false
        orgName.value = state.orgName ?: ""
        login.value = state.login ?: ""
        url.value = state.url ?: "http://circlet-api.labs.intellij.net"
    }

    override fun getState(): IdePLuginClientData =
        IdePLuginClientData(enabled = enabled.value, orgName = orgName.value, login = login.value, url = url.value)

}
