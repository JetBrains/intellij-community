package circlet.plugins.pipelines.viewmodel

import circlet.pipelines.config.api.*
import com.intellij.openapi.project.*
import libraries.coroutines.extra.*
import runtime.reactive.*
import java.util.*
import javax.swing.tree.*

class ScriptWindowViewModel(private val lifetime: Lifetime, private val project: Project) {
    val scriptLifetimes = SequentialLifetimes(lifetime)
    val modelBuildIsRunning = mutableProperty(false)
    val taskIsRunning = mutableProperty(false)
    val script = mutableProperty<ScriptViewModel?>(null)
    val selectedNode = mutableProperty<CircletModelTreeNode?>(null)
    val extendedViewModeEnabled = mutableProperty<Boolean>(true)
    val logBuildData = mutableProperty<LogData?>(null)
    val logRunData = mutableProperty<LogData?>(null)

    init {
        selectedNode.forEach(lifetime) {
            logRunData.value = if (it != null && it.isRunnable) LogData("todo: log of task `${it.userObject}` run") else null
        }
    }
}


class ScriptViewModel internal constructor(
    val id: String,
    val lifetime: Lifetime,
    val config: ProjectConfig) {
}

object ScriptViewModelFactory {
    fun create(lifetime: Lifetime, config: ProjectConfig) = ScriptViewModel(UUID.randomUUID().toString(), lifetime, config)

}

class LogData(val dummy: String) {
    val messages = ObservableList.mutable<String>()
    fun add(message: String) {
        messages.add(message)
    }
}


fun createEmptyScriptViewModel(lifetime: Lifetime) : ScriptViewModel {
    return ScriptViewModelFactory.create(lifetime, ProjectConfig(emptyList(), emptyList(), emptyList()))

}


class CircletModelTreeNode(text: String? = null, val isRunnable: Boolean = false) : DefaultMutableTreeNode(text) {

}
