// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.jps.model.serialization.PathMacroUtil
import kotlin.io.path.absolutePathString

@State(name = "PySdkSettings", storages = [Storage(value = "pySdk.xml", roamingType = RoamingType.DISABLED)])
class PySdkSettings : PersistentStateComponent<PySdkSettings.State> {
  companion object {
    @JvmStatic
    val instance: PySdkSettings
      get() = ApplicationManager.getApplication().getService(PySdkSettings::class.java)

    private const val VIRTUALENV_ROOT_DIR_MACRO_NAME = "VIRTUALENV_ROOT_DIR"
  }

  private val state: State = State()

  var useNewEnvironmentForNewProject: Boolean
    get() = state.USE_NEW_ENVIRONMENT_FOR_NEW_PROJECT
    set(value) {
      state.USE_NEW_ENVIRONMENT_FOR_NEW_PROJECT = value
    }

  var preferredEnvironmentType: String?
    get() = state.PREFERRED_ENVIRONMENT_TYPE
    set(value) {
      state.PREFERRED_ENVIRONMENT_TYPE = value
    }

  var preferredVirtualEnvBaseSdk: String?
    get() = state.PREFERRED_VIRTUALENV_BASE_SDK
    set(value) {
      state.PREFERRED_VIRTUALENV_BASE_SDK = value
    }

  fun onVirtualEnvCreated(baseSdk: Sdk, location: @SystemIndependent String, projectPath: @SystemIndependent String?) {
    setPreferredVirtualEnvBasePath(location, projectPath)
    preferredVirtualEnvBaseSdk = baseSdk.homePath
  }

  private fun setPreferredVirtualEnvBasePath(value: @SystemIndependent String, projectPath: @SystemIndependent String?) {
    val pathMap = ReplacePathToMacroMap().apply {
      projectPath?.let {
        addMacroReplacement(it, PathMacroUtil.PROJECT_DIR_MACRO_NAME)
      }
      addMacroReplacement(defaultVirtualEnvRoot, VIRTUALENV_ROOT_DIR_MACRO_NAME)
    }
    val pathToSave = when {
      projectPath != null && FileUtil.isAncestor(projectPath, value, true) -> value.trimEnd { !it.isLetter() }
      else -> PathUtil.getParentPath(value)
    }
    state.PREFERRED_VIRTUALENV_BASE_PATH = pathMap.substitute(pathToSave, true)
  }

  fun getPreferredVirtualEnvBasePath(projectPath: @SystemIndependent String?): @SystemIndependent String {
    val defaultPath = defaultVirtualEnvRoot
    val pathMap = ExpandMacroToPathMap().apply {
      addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectPath ?: userHome)
      addMacroExpand(VIRTUALENV_ROOT_DIR_MACRO_NAME, defaultPath)
    }
    val rawSavedPath = state.PREFERRED_VIRTUALENV_BASE_PATH ?: defaultPath
    val savedPath = pathMap.substitute(rawSavedPath, true)
    return when {
      projectPath != null && FileUtil.isAncestor(projectPath, savedPath, true) -> savedPath
      projectPath != null -> "$savedPath/${PathUtil.getFileName(projectPath)}"
      else -> savedPath
    }
  }

  override fun getState(): State = state

  override fun loadState(state: State) {
    XmlSerializerUtil.copyBean(state, this.state)
  }

  @Suppress("PropertyName")
  class State {
    @JvmField
    var USE_NEW_ENVIRONMENT_FOR_NEW_PROJECT: Boolean = true
    @JvmField
    var PREFERRED_ENVIRONMENT_TYPE: String? = null
    @JvmField
    var PREFERRED_VIRTUALENV_BASE_PATH: String? = null
    @JvmField
    var PREFERRED_VIRTUALENV_BASE_SDK: String? = null
  }

  private val defaultVirtualEnvRoot: @SystemIndependent String
    get() = VirtualEnvSdkFlavor.getDefaultLocation().absolutePathString()

  private val userHome: @SystemIndependent String
    get() = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
}