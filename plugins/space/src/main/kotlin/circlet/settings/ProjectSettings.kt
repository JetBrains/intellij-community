package circlet.settings

import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import runtime.reactive.*

@State(
    name = "CircletProjectSettings",
    storages = [Storage(value = "CircletClient.xml", roamingType = RoamingType.DISABLED)]
)
class ProjectSettings(project: Project) :
    AbstractProjectComponent(project), LifetimedComponent by SimpleLifetimedComponent(),
    PersistentStateComponent<ProjectSettings.MutableState> {

    data class State(
        val serverUrl: String = "",
        val projectKey: String = ""
    )

    data class MutableState(
        var serverUrl: String = "",
        var projectKey: String = ""
    )

    val serverUrl: MutableProperty<String> = mutableProperty("")
    val projectKey: MutableProperty<String> = mutableProperty("")

    val stateProperty = PropertyWithSuspendableUpdates(lifetime, State())

    init {
        with(stateProperty) {
            map(serverUrl) { previousState, newServerUrl -> previousState.copy(serverUrl = newServerUrl) }
            map(projectKey) { previousState, newProjectKey -> previousState.copy(projectKey = newProjectKey) }
        }
    }

    override fun getState(): MutableState = MutableState(
        serverUrl = serverUrl.value,
        projectKey = projectKey.value
    )

    override fun loadState(state: MutableState) {
        batchUpdate(stateProperty) {
            serverUrl.value = state.serverUrl
            projectKey.value = state.projectKey
        }
    }
}

val Project.settings: ProjectSettings get() = getService()

val ProjectSettings.State.isIntegrationAvailable: Boolean get() =
    serverUrl.isNotBlank() && projectKey.isNotBlank()

class PropertyWithSuspendableUpdates<T> private constructor(
    private val lifetime: Lifetime,
    private val internal: MutableProperty<T>,
    private val external: MutableProperty<T>
) : Property<T> by external {

    private var suspendUpdatesFlag: Boolean = false

    constructor(lifetime: Lifetime, initialValue: T) :
        this(lifetime, mutableProperty(initialValue), mutableProperty(initialValue))

    init {
        internal.forEach(lifetime) {
            if (!suspendUpdatesFlag) {
                external.value = it
            }
        }
    }

    fun <U> map(source: Source<U>, transform: (T, U) -> T) {
        source.forEach(lifetime) {
            internal.value = transform(internal.value, it)
        }
    }

    fun suspendUpdates() {
        suspendUpdatesFlag = true
    }

    fun resumeUpdates() {
        suspendUpdatesFlag = false

        external.value = internal.value
    }
}

private fun batchUpdate(vararg properties: PropertyWithSuspendableUpdates<*>, block: () -> Unit) {
    properties.forEach { it.suspendUpdates() }

    block()

    properties.forEach { it.resumeUpdates() }
}
