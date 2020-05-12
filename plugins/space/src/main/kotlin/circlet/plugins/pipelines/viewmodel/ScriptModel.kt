package circlet.plugins.pipelines.viewmodel

import circlet.pipelines.config.api.*
import com.intellij.build.events.*
import com.intellij.build.events.impl.*
import libraries.coroutines.extra.*
import libraries.io.random.*
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

    val buildId = Random.nextUID()

    val messages = ObservableList.mutable<BuildEvent>()

    private val _buildLifetime = LifetimeSource()

    fun message(message: String) {
        _buildLifetime.assertNotTerminated()
        messages.add(OutputBuildEventImpl(buildId, message + "\n", true))
    }

    fun error(message: String) {
        _buildLifetime.assertNotTerminated()
        messages.add(OutputBuildEventImpl(buildId, message + "\n", false))
    }

    fun close() {
        _buildLifetime.terminate()
    }

}

class CircletModelTreeNode(text: String? = null, val isRunnable: Boolean = false) : DefaultMutableTreeNode(text)
