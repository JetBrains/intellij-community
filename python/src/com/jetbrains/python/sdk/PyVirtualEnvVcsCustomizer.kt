// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.sdk.backend.activationEnvironment
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsEnvCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import org.jetbrains.annotations.ApiStatus

internal class PyVirtualEnvVcsCustomizer : VcsEnvCustomizer() {
  override fun customizeCommandAndEnvironment(project: Project?, envs: MutableMap<String, String>, context: VcsExecutableContext) {
    if (project == null || !PyVirtualEnvVcsSettings.getInstance(project).virtualEnvActivate) return

    // Use a cancellable, retryable read action instead of a plain blocking one:
    // this runs in a git command (background thread) and must yield to pending write actions
    // to avoid non-cancellable read-action freezes (PY-89735).
    val interpreter = ReadAction.nonBlocking<PythonInterpreter?> { findInterpreter(project, context.root) }.executeSynchronously() ?: return
    val native = when (interpreter) {
      is NativePythonInterpreter -> interpreter
      // A target interpreter has no environment that a local git can activate.
      is TargetPythonInterpreter -> return
    }
    if (context.type != ExecutableType.LOCAL) return

    if (native.isActivatable) {
      // in case of virtualenv sdk on unix we activate virtualenv
      envs.putAll(runBlockingMaybeCancellable { native.activationEnvironment() }.successOrNull ?: emptyMap())
    }
  }

  override fun getConfigurable(project: Project?): UnnamedConfigurable? {
    if (project == null) return null
    return object : UiDslUnnamedConfigurable.Simple() {
      override fun Panel.createContent() {
        val settings = PyVirtualEnvVcsSettings.getInstance(project)
        row {
          checkBox(PyBundle.message("vcs.activate.virtualenv.checkbox.text"))
            .bindSelected(settings::virtualEnvActivate)
        }
      }
    }
  }

  /**
   * The interpreter of the Python project of [root], else of the main Python project. Read from the snapshot, because
   * a git command cannot wait. Before the first snapshot, nothing is activated.
   */
  private fun findInterpreter(project: Project, root: VirtualFile?): PythonInterpreter? {
    val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return null
    return root?.let { snapshot.forFile(it, mainForOrphans = false) }?.interpreter ?: snapshot.main?.interpreter
  }
}

@Service(Service.Level.PROJECT)
@State(name = "PyVirtualEnvVcsSettings", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
@ApiStatus.Internal
class PyVirtualEnvVcsSettings : BaseState(), PersistentStateComponent<PyVirtualEnvVcsSettings> {
  override fun getState(): PyVirtualEnvVcsSettings = this

  override fun loadState(state: PyVirtualEnvVcsSettings) {
    copyFrom(state)
  }

  var virtualEnvActivate: Boolean by property(true)

  companion object {
    fun getInstance(project: Project): PyVirtualEnvVcsSettings = project.service()
  }
}
