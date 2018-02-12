/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.sdk

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.jps.model.serialization.PathMacroUtil

/**
 * @author vlan
 */
@State(name = "PySdkSettings", storages = arrayOf(Storage(value = "py_sdk_settings.xml", roamingType = RoamingType.DISABLED)))
class PySdkSettings : PersistentStateComponent<PySdkSettings.State> {
  companion object {
    @JvmStatic
    val instance: PySdkSettings
      get() = ServiceManager.getService(PySdkSettings::class.java)

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

  fun setPreferredVirtualEnvBasePath(value: @SystemIndependent String, projectPath: @SystemIndependent String) {
    val pathMap = ReplacePathToMacroMap().apply {
      addMacroReplacement(projectPath, PathMacroUtil.PROJECT_DIR_MACRO_NAME)
      addMacroReplacement(defaultVirtualEnvRoot, VIRTUALENV_ROOT_DIR_MACRO_NAME)
    }
    val pathToSave = when {
      FileUtil.isAncestor(projectPath, value, true) -> value.trimEnd { !it.isLetter() }
      else -> PathUtil.getParentPath(value)
    }
    state.PREFERRED_VIRTUALENV_BASE_PATH = pathMap.substitute(pathToSave, true)
  }

  fun getPreferredVirtualEnvBasePath(projectPath: @SystemIndependent String): @SystemIndependent String {
    val pathMap = ExpandMacroToPathMap().apply {
      addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectPath)
      addMacroExpand(VIRTUALENV_ROOT_DIR_MACRO_NAME, defaultVirtualEnvRoot)
    }
    val defaultPath = when {
      defaultVirtualEnvRoot != userHome -> defaultVirtualEnvRoot
      else -> "$${PathMacroUtil.PROJECT_DIR_MACRO_NAME}$/venv"
    }
    val rawSavedPath = state.PREFERRED_VIRTUALENV_BASE_PATH ?: defaultPath
    val savedPath = pathMap.substitute(rawSavedPath, true)
    return when {
      FileUtil.isAncestor(projectPath, savedPath, true) -> savedPath
      else -> "$savedPath/${PathUtil.getFileName(projectPath)}"
    }
  }

  override fun getState() = state

  override fun loadState(state: PySdkSettings.State) {
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
    get() = VirtualEnvSdkFlavor.getDefaultLocation()?.path ?: userHome

  private val userHome: @SystemIndependent String
    get() = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
}