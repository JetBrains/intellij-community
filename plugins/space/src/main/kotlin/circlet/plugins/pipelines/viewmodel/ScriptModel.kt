package circlet.plugins.pipelines.viewmodel

import circlet.pipelines.config.api.*
import libraries.coroutines.extra.*
import runtime.reactive.*
import javax.swing.tree.*


enum class ScriptState { NotInitialised, Building, Ready }

interface ScriptModel {
    val config: Property<ScriptConfig?>
    val error: Property<String?>
    val state: Property<ScriptState>
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

class CircletModelTreeNode(text: String? = null, val isRunnable: Boolean = false) : DefaultMutableTreeNode(text)
