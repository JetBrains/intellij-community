// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.PathMappingSettings
import com.intellij.util.containers.ComparatorUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import com.jetbrains.python.console.actions.CommandQueueForPythonConsoleService
import com.jetbrains.python.run.AbstractPyCommonOptionsForm
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams
import com.jetbrains.python.run.PythonRunParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@State(
  name = "PyConsoleOptionsProvider",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class PyConsoleOptions(private val project: Project) : PersistentStateComponent<PyConsoleOptions.State> {
  private val myState: State = State()

  val pythonConsoleSettings: PyConsoleSettings get() = myState.myPythonConsoleState

  var isShowDebugConsoleByDefault: Boolean
    get() = myState.myShowDebugConsoleByDefault
    set(value) {
      myState.myShowDebugConsoleByDefault = value
    }

  var isShowVariableByDefault: Boolean
    get() = myState.myShowVariablesByDefault
    @JvmName("setShowVariablesByDefault")
    set(value) {
      myState.myShowVariablesByDefault = value
    }

  var isIpythonEnabled: Boolean
    get() = myState.myIpythonEnabled
    set(value) {
      myState.myIpythonEnabled = value
    }

  var isUseExistingConsole: Boolean
    get() = myState.myUseExistingConsole
    set(value) {
      myState.myUseExistingConsole = value
    }

  var isCommandQueueEnabled: Boolean
    get() = myState.myCommandQueueEnabled
    set(value) {
      myState.myCommandQueueEnabled = value
      if (!value) {
        project.getService(CommandQueueForPythonConsoleService::class.java).disableCommandQueue()
      }
    }

  var codeCompletionOption: PyConsoleOptionsConfigurable.CodeCompletionOption
    get() = myState.myCodeCompletionOption
    set(value) {
      myState.myCodeCompletionOption = value
    }

  val isRuntimeCodeCompletion: Boolean
    get() = myState.myCodeCompletionOption == PyConsoleOptionsConfigurable.CodeCompletionOption.RUNTIME

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState.myShowDebugConsoleByDefault = state.myShowDebugConsoleByDefault
    myState.myShowVariablesByDefault = state.myShowVariablesByDefault
    myState.myPythonConsoleState = state.myPythonConsoleState
    myState.myIpythonEnabled = state.myIpythonEnabled
    myState.myUseExistingConsole = state.myUseExistingConsole
    myState.myCommandQueueEnabled = state.myCommandQueueEnabled
    myState.myCodeCompletionOption = state.myCodeCompletionOption
  }

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyConsoleOptions = project.getService(PyConsoleOptions::class.java)
  }

  class State {
    @JvmField
    var myPythonConsoleState: PyConsoleSettings = PyConsoleSettings()

    @JvmField
    var myShowDebugConsoleByDefault: Boolean = true

    @JvmField
    var myShowVariablesByDefault: Boolean = true

    @JvmField
    var myIpythonEnabled: Boolean = true

    @JvmField
    var myUseExistingConsole: Boolean = false

    @JvmField
    var myCommandQueueEnabled: Boolean = false

    @JvmField
    var myCodeCompletionOption: PyConsoleOptionsConfigurable.CodeCompletionOption =
      PyConsoleOptionsConfigurable.CodeCompletionOption.STATIC
  }

  @Tag("console-settings")
  class PyConsoleSettings() : PythonRunParams {
    @JvmField
    var myCustomStartScript: String = PydevConsoleRunnerImpl.CONSOLE_START_COMMAND

    @JvmField
    var mySdkHome: String? = null

    @JvmField
    var mySdk: Sdk? = null

    @JvmField
    var myInterpreterOptions: String = ""

    @JvmField
    var myUseModuleSdk: Boolean = false

    @JvmField
    var myModuleName: String? = null

    @JvmField
    var myEnvs: MutableMap<String, String> = HashMap()

    @JvmField
    var myEnvFiles: List<String> = emptyList()

    @JvmField
    var myPassParentEnvs: Boolean = true

    @JvmField
    var myWorkingDirectory: String = ""

    @JvmField
    var myAddContentRoots: Boolean = true

    @JvmField
    var myAddSourceRoots: Boolean = true

    private var myMappings: PathMappingSettings = PathMappingSettings()
    private var myUseSoftWraps: Boolean = false
    private var myDebugJustMyCode: Boolean = false

    constructor(myCustomStartScript: String) : this() {
      this.myCustomStartScript = myCustomStartScript
    }

    fun apply(form: AbstractPythonRunConfigurationParams) {
      mySdkHome = form.sdkHome
      mySdk = form.sdk
      myInterpreterOptions = form.interpreterOptions
      myEnvs = form.envs
      myEnvFiles = form.envFilePaths
      myPassParentEnvs = form.isPassParentEnvs
      myUseModuleSdk = form.isUseModuleSdk
      myModuleName = form.module?.name
      myWorkingDirectory = form.workingDirectory

      myAddContentRoots = form.shouldAddContentRoots()
      myAddSourceRoots = form.shouldAddSourceRoots()
      myMappings = form.mappingSettings ?: PathMappingSettings()
    }

    fun isModified(form: AbstractPyCommonOptionsForm): Boolean {
      return !ComparatorUtil.equalsNullable(mySdkHome, form.sdkHome) ||
             myInterpreterOptions != form.interpreterOptions ||
             myEnvs != form.envs ||
             myEnvFiles != form.envFilePaths ||
             myPassParentEnvs != form.isPassParentEnvs ||
             myUseModuleSdk != form.isUseModuleSdk ||
             myAddContentRoots != form.shouldAddContentRoots() ||
             myAddSourceRoots != form.shouldAddSourceRoots() ||
             !ComparatorUtil.equalsNullable(myModuleName, form.module?.name) ||
             myWorkingDirectory != form.workingDirectory ||
             myMappings != form.mappingSettings
    }

    fun reset(project: Project, form: AbstractPythonRunConfigurationParams) {
      form.envs = myEnvs
      form.envFilePaths = myEnvFiles
      form.isPassParentEnvs = myPassParentEnvs
      form.interpreterOptions = myInterpreterOptions
      form.sdkHome = mySdkHome
      form.sdk = mySdk
      form.isUseModuleSdk = myUseModuleSdk
      form.setAddContentRoots(myAddContentRoots)
      form.setAddSourceRoots(myAddSourceRoots)

      var moduleWasAutoselected = false
      if (form.isUseModuleSdk != myUseModuleSdk) {
        myUseModuleSdk = form.isUseModuleSdk
        moduleWasAutoselected = true
      }

      myModuleName?.let {
        form.module = ModuleManager.getInstance(project).findModuleByName(it)
      }

      form.module?.takeIf { moduleWasAutoselected }?.let {
        myModuleName = it.name
      }

      form.workingDirectory = myWorkingDirectory

      form.mappingSettings = myMappings
    }

    @Suppress("unused")
    @Attribute("custom-start-script")
    fun getCustomStartScript(): String = myCustomStartScript

    @Attribute("sdk-home")
    override fun getSdkHome(): String? = mySdkHome

    override fun getSdk(): Sdk? = mySdk

    @Attribute("module-name")
    override fun getModuleName(): String? = myModuleName

    @Attribute("working-directory")
    override fun getWorkingDirectory(): String = myWorkingDirectory

    @Attribute("is-module-sdk")
    override fun isUseModuleSdk(): Boolean = myUseModuleSdk

    @XMap(propertyElementName = "envs", entryTagName = "env")
    override fun getEnvs(): MutableMap<String, String> = myEnvs

    @Attribute("add-content-roots")
    override fun shouldAddContentRoots(): Boolean = myAddContentRoots

    @Attribute("add-source-roots")
    override fun shouldAddSourceRoots(): Boolean = myAddSourceRoots

    @Attribute("interpreter-options")
    override fun getInterpreterOptions(): String = myInterpreterOptions

    @XCollection
    fun getMappings(): PathMappingSettings = myMappings

    @Suppress("unused")
    fun setCustomStartScript(customStartScript: String) {
      myCustomStartScript = customStartScript
    }

    override fun setSdkHome(sdkHome: String?) {
      mySdkHome = sdkHome
    }

    override fun setSdk(sdk: Sdk?) {
      mySdk = sdk
    }

    override fun setModule(module: Module) {
      setModuleName(module.name)
    }

    override fun setInterpreterOptions(interpreterOptions: String) {
      myInterpreterOptions = interpreterOptions
    }

    override fun setUseModuleSdk(useModuleSdk: Boolean) {
      myUseModuleSdk = useModuleSdk
    }

    @Attribute("is-pass-parent-envs")
    override fun isPassParentEnvs(): Boolean = myPassParentEnvs

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
      myPassParentEnvs = passParentEnvs
    }

    fun setModuleName(moduleName: String?) {
      myModuleName = moduleName
    }

    override fun setEnvs(envs: MutableMap<String, String>) {
      myEnvs = envs
    }

    override fun getMappingSettings(): PathMappingSettings = getMappings()

    override fun setMappingSettings(mappingSettings: PathMappingSettings?) {
    }

    override fun setWorkingDirectory(workingDirectory: String) {
      myWorkingDirectory = workingDirectory
    }

    override fun setAddContentRoots(addContentRoots: Boolean) {
      myAddContentRoots = addContentRoots
    }

    override fun setAddSourceRoots(addSourceRoots: Boolean) {
      myAddSourceRoots = addSourceRoots
    }

    @ApiStatus.Internal
    override fun shouldDebugJustMyCode(): Boolean = myDebugJustMyCode

    @ApiStatus.Internal
    override fun setDebugJustMyCode(debugJustMyCode: Boolean) {
      myDebugJustMyCode = debugJustMyCode
    }

    @Suppress("unused")
    fun setMappings(mappings: PathMappingSettings?) {
      myMappings = mappings ?: PathMappingSettings()
    }

    var isUseSoftWraps: Boolean
      get() = myUseSoftWraps
      set(value) {
        myUseSoftWraps = value
      }

    override var envFilePaths: List<String>
      get() = myEnvFiles
      set(value) {
        myEnvFiles = value
      }
  }
}
