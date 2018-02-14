package circlet.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.*

@State(name = "CircletProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class CircletProjectSettings(
    @Suppress("unused") private val project: Project
) : PersistentStateComponent<CircletProjectSettings.State> {

    data class State(var serverUrl: String = "")

    var currentState: State = State()

    override fun getState(): State? = currentState

    override fun loadState(state: State) {
        currentState = state
    }
}
