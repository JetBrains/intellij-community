// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.pipenv.pipEnvPath
import com.jetbrains.python.sdk.poetry.poetryPath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Path
import kotlin.io.path.pathString


@OptIn(ExperimentalCoroutinesApi::class)
abstract class PythonAddInterpreterModel(params: PyInterpreterModelParams) {

  val propertyGraph = PropertyGraph()
  val navigator = PythonNewEnvironmentDialogNavigator()
  open val state = AddInterpreterState(propertyGraph)
  open val targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null

  val projectPath = params.projectPathProperty ?: propertyGraph.property("") // todo how to populate?
  internal val scope = params.scope
  internal val uiContext = params.uiContext

  internal val knownInterpreters: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  private val _detectedInterpreters: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  val detectedInterpreters: StateFlow<List<PythonSelectableInterpreter>> = _detectedInterpreters
  val manuallyAddedInterpreters: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  private var installable: List<PythonSelectableInterpreter> = emptyList()
  val condaEnvironments: MutableStateFlow<List<PyCondaEnv>> = MutableStateFlow(emptyList())

  var allInterpreters: StateFlow<List<PythonSelectableInterpreter>> = combine(knownInterpreters,
                                                                              detectedInterpreters,
                                                                              manuallyAddedInterpreters) { known, detected, added ->
    added + known + detected
  }
    .stateIn(scope + uiContext, started = SharingStarted.Eagerly, initialValue = emptyList())


  val baseInterpreters: StateFlow<List<PythonSelectableInterpreter>> = allInterpreters
    .mapLatest {
      it.filter { it !is ExistingSelectableInterpreter || it.isSystemWide } + installable
    }
    .stateIn(scope + uiContext, started = SharingStarted.Eagerly, initialValue = emptyList())


  val interpreterLoading = MutableStateFlow(false)
  val condaEnvironmentsLoading = MutableStateFlow(false)

  open fun createBrowseAction(): () -> String? = TODO()

  open suspend fun initialize() {
    interpreterLoading.value = true
    initInterpreterList()
    detectCondaExecutable()
    condaEnvironmentsLoading.value = true
    detectCondaEnvironments()
    condaEnvironmentsLoading.value = false
    interpreterLoading.value = false
  }


  suspend fun detectCondaExecutable() {
    withContext(Dispatchers.IO) {
      val executor = targetEnvironmentConfiguration.toExecutor()
      val suggestedCondaPath = runCatching {
        suggestCondaPath(targetCommandExecutor = executor)
      }.getOrLogException(PythonAddInterpreterPresenter.LOG)
      val suggestedCondaLocalPath = suggestedCondaPath?.toLocalPathOn(targetEnvironmentConfiguration)
      withContext(uiContext) {
        state.condaExecutable.set(suggestedCondaLocalPath?.toString().orEmpty())
      }

      //val environments = suggestedCondaPath?.let { PyCondaEnv.getEnvs(executor, suggestedCondaPath).getOrLogException(
      //  PythonAddInterpreterPresenter.LOG) }
      //baseConda = environments?.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }

    }
  }
  suspend fun detectCondaEnvironments() {
    withContext(Dispatchers.IO) {
      val commandExecutor = targetEnvironmentConfiguration.toExecutor()
      val environments = PyCondaEnv.getEnvs(commandExecutor, state.condaExecutable.get()).getOrLogException(LOG) ?: emptyList()
      val baseConda = environments.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }

      withContext(uiContext) {
        condaEnvironments.value = environments
        state.baseCondaEnv.set(baseConda)
      }
    }
  }



  suspend fun initInterpreterList() {
    withContext(Dispatchers.IO) {
      val existingSdks = PyConfigurableInterpreterList.getInstance(null).getModel().sdks.toList()
      val allValidSdks = ProjectSpecificSettingsStep.getValidPythonSdks(existingSdks)
        .map { ExistingSelectableInterpreter(it, PySdkUtil.getLanguageLevelForSdk(it), it.isSystemWide) }

      val languageLevels = allValidSdks.mapTo(HashSet()) { it.languageLevel } // todo add detected here
      val filteredInstallable = getSdksToInstall()
        .map { LanguageLevel.fromPythonVersion(it.versionString) to it }
        .filter { it.first !in languageLevels }
        .sortedByDescending { it.first }
        .map { InstallableSelectableInterpreter(it.second) }


      val existingSdkPaths = existingSdks.mapNotNull { it.homePath }.mapNotNull { tryResolvePath(it) }.toSet()

      val detected = PythonSdkFlavor.getApplicableFlavors(true).flatMap { it.suggestLocalHomePaths(null, null) }
        .filterNot { it in existingSdkPaths }
        .map { DetectedSelectableInterpreter(it.pathString) }

      withContext(uiContext) {
        installable = filteredInstallable
        knownInterpreters.value = allValidSdks // todo check target?
        _detectedInterpreters.value = detected
      }

    }
  }

  open fun addInterpreter(path: String): PythonSelectableInterpreter = TODO()

  open fun suggestVenvPath(): String? = ""
}


abstract class PythonMutableTargetAddInterpreterModel(params: PyInterpreterModelParams)
  : PythonAddInterpreterModel(params) {
  override val state: MutableTargetState = MutableTargetState(propertyGraph)

  override suspend fun initialize() {
    super.initialize()
    detectPoetryExecutable()
    detectPipEnvExecutable()
  }

  suspend fun detectPoetryExecutable() {
    // todo this is local case, fix for targets
    val savedPath = PropertiesComponent.getInstance().poetryPath
    if (savedPath != null) {
      state.poetryExecutable.set(savedPath)
    }
    else {
      val modalityState = ModalityState.current().asContextElement()
      scope.launch(Dispatchers.IO) {
        val poetryExecutable = com.jetbrains.python.sdk.poetry.detectPoetryExecutable()
        withContext(Dispatchers.EDT + modalityState) {
          poetryExecutable?.let { state.poetryExecutable.set(it.pathString) }
        }
      }
    }
  }

  suspend fun detectPipEnvExecutable() {
    // todo this is local case, fix for targets
    val savedPath = PropertiesComponent.getInstance().pipEnvPath
    if (savedPath != null) {
      state.pipenvExecutable.set(savedPath)
    }
    else {
      val modalityState = ModalityState.current().asContextElement()
      scope.launch(Dispatchers.IO) {
        val detectedExecutable = com.jetbrains.python.sdk.pipenv.detectPipEnvExecutable()
        withContext(Dispatchers.EDT + modalityState) {
          detectedExecutable?.let { state.pipenvExecutable.set(it.path) }
        }
      }
    }
  }
}


class PythonLocalAddInterpreterModel(params: PyInterpreterModelParams)
  : PythonMutableTargetAddInterpreterModel(params) {

  override suspend fun initialize() {
    super.initialize()

    val mostRecentlyUsedBasePath = PySdkSettings.instance.preferredVirtualEnvBaseSdk
    val interpreterToSelect = detectedInterpreters.value.find { it.homePath == mostRecentlyUsedBasePath }
                              ?: baseInterpreters.value
                                .filterIsInstance<ExistingSelectableInterpreter>()
                                .maxByOrNull { it.languageLevel }

    if (interpreterToSelect != null) {
      state.baseInterpreter.set(interpreterToSelect)
    }
  }

  override fun createBrowseAction(): () -> String? {
    return {
      var path: Path? = null
      FileChooser.chooseFile(PythonSdkType.getInstance().homeChooserDescriptor, null, null) { file ->
        path = file?.toNioPath()
      }
      path?.toString()
    }
  }

  override fun addInterpreter(path: String): PythonSelectableInterpreter {
    val interpreter = ManuallyAddedSelectableInterpreter(path)
    manuallyAddedInterpreters.value += interpreter
    return interpreter
  }

  override fun suggestVenvPath(): String? {
    // todo should this be a coroutine?
    return FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectPath.get()))
  }
}


// todo does it need target configuration
abstract class PythonSelectableInterpreter {
  abstract val homePath: String
  override fun toString(): String =
    "PythonSelectableInterpreter(homePath='$homePath')"
}

class ExistingSelectableInterpreter(val sdk: Sdk, val languageLevel: LanguageLevel, val isSystemWide: Boolean) : PythonSelectableInterpreter() {
  override val homePath = sdk.homePath!! // todo is it safe
}


class DetectedSelectableInterpreter(override val homePath: String) : PythonSelectableInterpreter()

class ManuallyAddedSelectableInterpreter(override val homePath: String) : PythonSelectableInterpreter()
class InstallableSelectableInterpreter(val sdk: PySdkToInstall) : PythonSelectableInterpreter() {
  override val homePath: String = ""
}


class InterpreterSeparator(val text: String) : PythonSelectableInterpreter() {
  override val homePath: String = ""
}


open class AddInterpreterState(propertyGraph: PropertyGraph) {
  val selectedInterpreter: ObservableMutableProperty<PythonSelectableInterpreter?> = propertyGraph.property(null)
  val condaExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val selectedCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)
  val baseCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)
}

class MutableTargetState(propertyGraph: PropertyGraph) : AddInterpreterState(propertyGraph) {
  val baseInterpreter: ObservableMutableProperty<PythonSelectableInterpreter?> = propertyGraph.property(null)
  val newCondaEnvName: ObservableMutableProperty<String> = propertyGraph.property("")
  val poetryExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val pipenvExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val venvPath: ObservableMutableProperty<String> = propertyGraph.property("")
  val inheritSitePackages = propertyGraph.property(false)
  val makeAvailable = propertyGraph.property(false)
}


val PythonAddInterpreterModel.existingSdks
  get() = allInterpreters.value.filterIsInstance<ExistingSelectableInterpreter>().map { it.sdk }

val PythonAddInterpreterModel.baseSdks
  get() = baseInterpreters.value.filterIsInstance<ExistingSelectableInterpreter>().map { it.sdk }

fun PythonAddInterpreterModel.findInterpreter(path: String): PythonSelectableInterpreter? {
  return allInterpreters.value.asSequence().find { it.homePath == path }
}