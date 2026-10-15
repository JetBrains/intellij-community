// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

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
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vcs.VcsEnvCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.wsl.WslConstants
import com.intellij.remote.RemoteSdkAdditionalData
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI

internal class PyVirtualEnvVcsCustomizer : VcsEnvCustomizer() {
  override fun customizeCommandAndEnvironment(project: Project?, envs: MutableMap<String, String>, context: VcsExecutableContext) {
    if (project == null || !PyVirtualEnvVcsSettings.getInstance(project).virtualEnvActivate) return

    // Use a cancellable, retryable read action instead of a plain blocking one:
    // this runs in a git command (background thread) and must yield to pending write actions
    // to avoid non-cancellable read-action freezes (PY-89735).
    val interpreter = ReadAction.nonBlocking<PythonInterpreter?> { findInterpreter(project, context.root) }.executeSynchronously() ?: return
    @Suppress("DEPRECATION") // The remote and WSL checks and the activation environment read an SDK.
    val sdk = interpreter.getSdkAPI()
    when {
      PythonSdkUtil.isRemote(sdk) -> return
      sdk.isWsl -> if (context.type != ExecutableType.WSL) return
      else -> if (context.type != ExecutableType.LOCAL) return
    }

    if (interpreter is NativePythonInterpreter && interpreter.isActivatable) {
      // in case of virtualenv sdk on unix we activate virtualenv
      envs.putAll(runBlockingMaybeCancellable { sdk.activationEnvironment() }.successOrNull ?: emptyMap())
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

  private val Sdk.isWsl: Boolean
    // WSLCredentialsType#WZSL_CREDENTIALS_PREFIX is inaccessible, let's use WslConstants.UNC_PREFIX
    get() = (sdkAdditionalData as? RemoteSdkAdditionalData)?.remoteConnectionType?.hasPrefix(WslConstants.UNC_PREFIX) == true
}

@Service(Service.Level.PROJECT)
@State(name = "PyVirtualEnvVcsSettings", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
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
