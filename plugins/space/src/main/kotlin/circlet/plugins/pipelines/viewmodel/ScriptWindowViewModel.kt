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

class LogData {

    // lifetime corresponding to the entire build process, it terminates when this build finishes
    val lifetime get() = _buildLifetime

    val messages = ObservableList.mutable<String>()

    private val _buildLifetime = LifetimeSource()

    fun add(message: String) {
        _buildLifetime.assertNotTerminated()
        messages.add(message)
    }

    fun close() {
        _buildLifetime.terminate()
    }

}


fun createEmptyScriptViewModel(): ScriptViewModel {
    return ScriptViewModelFactory.create(ScriptConfig(emptyList(), emptyList(), emptyList()))
}


class CircletModelTreeNode(text: String? = null, val isRunnable: Boolean = false) : DefaultMutableTreeNode(text) {

}
