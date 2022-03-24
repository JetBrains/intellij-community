// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vcs.VcsEnvCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.remote.RemoteSdkAdditionalData
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle

class PyVirtualEnvVcsCustomizer : VcsEnvCustomizer() {
  override fun customizeCommandAndEnvironment(project: Project?, envs: MutableMap<String, String>, context: VcsExecutableContext) {
    if (project == null || !PyVirtualEnvVcsSettings.getInstance(project).virtualEnvActivate) return

    val sdk: Sdk = runReadAction { findSdk(project, context.root) } ?: return
    when {
      PythonSdkUtil.isRemote(sdk) -> return
      sdk.isWsl -> if (context.type != ExecutableType.WSL) return
      else -> if (context.type != ExecutableType.LOCAL) return
    }

    if (PythonSdkUtil.isVirtualEnv(sdk) || PythonSdkUtil.isConda(sdk)) {
      // in case of virtualenv sdk on unix we activate virtualenv
      if (sdk.homePath != null) {
        envs.putAll(PySdkUtil.activateVirtualEnv(sdk))
      }
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

  private fun findSdk(project: Project, root: VirtualFile?): Sdk? {
    val module = root?.let { ModuleUtil.findModuleForFile(root, project) }
    if (module != null) return PythonSdkUtil.findPythonSdk(module)

    for (m in ModuleManager.getInstance(project).modules) {
      val sdk = PythonSdkUtil.findPythonSdk(m)
      if (sdk != null) return sdk
    }

    return null
  }

  private val Sdk.isWsl: Boolean
    get() = (sdkAdditionalData as? RemoteSdkAdditionalData<*>)?.remoteConnectionType?.hasPrefix("wsl://") == true
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
