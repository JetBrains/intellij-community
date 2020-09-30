package com.intellij.space.plugins.pipelines.viewmodel

import circlet.pipelines.config.api.ScriptConfig
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.OutputBuildEventImpl
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.assertNotTerminated
import libraries.io.random.Random
import runtime.reactive.ObservableList
import runtime.reactive.Property
import javax.swing.tree.DefaultMutableTreeNode


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

class SpaceModelTreeNode(text: String? = null, val isRunnable: Boolean = false) : DefaultMutableTreeNode(text)
