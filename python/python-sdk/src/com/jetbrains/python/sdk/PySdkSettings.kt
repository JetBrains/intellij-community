package com.jetbrains.python.sdk

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.python.venvReader.VirtualEnvReader
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

  var preferredVirtualEnvBaseSdk: String?
    get() = state.PREFERRED_VIRTUALENV_BASE_SDK
    set(value) {
      state.PREFERRED_VIRTUALENV_BASE_SDK = value
    }

  fun onVirtualEnvCreated(sdkPath: String?, location: @SystemIndependent String, projectPath: @SystemIndependent String?) {
    setPreferredVirtualEnvBasePath(location, projectPath)
    preferredVirtualEnvBaseSdk = sdkPath
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

    val rawSavedPath = state.PREFERRED_VIRTUALENV_BASE_PATH
    val savedPath = rawSavedPath?.takeIf { it.isNotEmpty() }?.let { pathMap.substitute(it, true) }
    val venvInProject = projectPath?.let { "$it/${VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME}" }

    return when {
      // if no venv has been created yet try to create in the project dir; otherwise default to ~/.virtualenvs
      savedPath == null -> pathMap.substitute(venvInProject ?: defaultPath, true)
      // if we already created venv and it aligns with the project path, use it
      projectPath != null && FileUtil.isAncestor(projectPath, savedPath, true) -> savedPath
      // otherwise use a known path but try to add the project name as a folder
      else -> savedPath + projectPath?.let { "/${PathUtil.getFileName(it)}" }
    }
  }

  override fun getState(): State = state

  override fun loadState(state: State) {
    XmlSerializerUtil.copyBean(state, this.state)
  }

  @Suppress("PropertyName")
  class State {

    @JvmField
    var PREFERRED_VIRTUALENV_BASE_PATH: String? = null

    @JvmField
    var PREFERRED_VIRTUALENV_BASE_SDK: String? = null
  }

  private val defaultVirtualEnvRoot: @SystemIndependent String
    get() = VirtualEnvReader.Instance.getVEnvRootDir().absolutePathString()

  private val userHome: @SystemIndependent String
    get() = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
}