package circlet.plugins.pipelines.viewmodel

import circlet.pipelines.config.api.*
import circlet.plugins.pipelines.services.*
import libraries.coroutines.extra.*
import runtime.reactive.*
import java.util.*
import javax.swing.tree.*

class ScriptViewModel internal constructor(
    val id: String,
    val config: ScriptConfig) {
}

object ScriptViewModelFactory {
    fun create(config: ScriptConfig) = ScriptViewModel(UUID.randomUUID().toString(), config)

}

class LogData(val dummy: String) {
    val messages = ObservableList.mutable<String>()
    fun add(message: String) {
        messages.add(message)
    }
}


fun createEmptyScriptViewModel() : ScriptViewModel {
    return ScriptViewModelFactory.create(ScriptConfig(emptyList(), emptyList(), emptyList()))
}


class CircletModelTreeNode(text: String? = null, val isRunnable: Boolean = false) : DefaultMutableTreeNode(text) {

}
