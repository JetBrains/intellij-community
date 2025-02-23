// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.python.community.services.shared.PythonWithLanguageLevel
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.UICustomization
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.failure
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.pipenv.getPipEnvExecutable
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString


@OptIn(ExperimentalCoroutinesApi::class)
abstract class PythonAddInterpreterModel(params: PyInterpreterModelParams, private val systemPythonService: SystemPythonService = SystemPythonService()) {

  val propertyGraph = PropertyGraph()
  val navigator = PythonNewEnvironmentDialogNavigator()
  open val state = AddInterpreterState(propertyGraph)
  open val targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null

  // TODO: DOC
  val myProjectPathFlows: ProjectPathFlows = params.projectPathFlows

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
    .map { it.filter { it.isBasePython() } }
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
    }
  }

  /**
   * Returns error or `null` if no error
   */
  suspend fun detectCondaEnvironments(): @NlsSafe String? =
    withContext(Dispatchers.IO) {
      val commandExecutor = targetEnvironmentConfiguration.toExecutor()
      val fullCondaPathOnTarget = state.condaExecutable.get()
      if (fullCondaPathOnTarget.isBlank()) return@withContext message("python.sdk.conda.no.exec")
      val environments = PyCondaEnv.getEnvs(commandExecutor, fullCondaPathOnTarget).getOrElse { return@withContext it.localizedMessage }
      val baseConda = environments.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }

      withContext(uiContext) {
        condaEnvironments.value = environments
        state.baseCondaEnv.set(baseConda)
      }
      return@withContext null
    }

  private suspend fun initInterpreterList() {
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


      // Venvs are not detected manually, but must migrate to VenvService or so
      val venvs: List<PythonWithLanguageLevel> = PythonWithLanguageLevelImpl.createByPythonBinaries(
        VirtualEnvSdkFlavor.getInstance()
          .suggestLocalHomePaths(null, null)
      ).mapNotNull { (venv, r) ->
        when (r) {
          is com.jetbrains.python.Result.Failure -> {
            fileLogger().warn("Skipping $venv : ${r.error}")
            null
          }
          is com.jetbrains.python.Result.Success -> r.result
        }
      }

      // System (base) pythons
      val system: List<SystemPython> = systemPythonService.findSystemPythons()

      // Python + isBase. Both: system and venv.
      val detected = (venvs.map { Triple(it, false, null) } + system.map { Triple(it, true, it.ui) })
        .filterNot { (python, _) -> python.pythonBinary in existingSdkPaths }
        .map { (python, base, ui) -> DetectedSelectableInterpreter(python.pythonBinary.pathString, python.languageLevel, base, ui) }
        .sorted()

      withContext(uiContext) {
        installable = filteredInstallable
        knownInterpreters.value = allValidSdks
        _detectedInterpreters.value = detected
      }

    }
  }

  open fun addInterpreter(path: String): PythonSelectableInterpreter {
    val languageLevel = PySdkUtil.getLanguageLevelForSdk(PythonSdkUtil.findSdkByKey(path))
    val interpreter = ManuallyAddedSelectableInterpreter(path, languageLevel)
    manuallyAddedInterpreters.value += interpreter
    return interpreter
  }

  open fun addInterpreter(sdk: Sdk) {
    manuallyAddedInterpreters.value += ExistingSelectableInterpreter(sdk, PySdkUtil.getLanguageLevelForSdk(sdk), sdk.isSystemWide)
  }

  /**
   * Given [pathToPython] returns either cleaned path (if valid) or null and reports error to [errorSink]
   */
  suspend fun getSystemPythonFromSelection(pathToPython: String, errorSink: ErrorSink): String? {
    val result = try {
      when (val r = systemPythonService.registerSystemPython(Path(pathToPython))) {
        is com.jetbrains.python.Result.Failure -> com.jetbrains.python.errorProcessing.failure(r.error)
        is com.jetbrains.python.Result.Success -> com.jetbrains.python.Result.success(r.result)
      }
    }
    catch (e: InvalidPathException) {
      com.jetbrains.python.errorProcessing.failure(e.localizedMessage)
    }

    return when (result) {
      is com.jetbrains.python.Result.Success -> result.result.pythonBinary.pathString
      is com.jetbrains.python.Result.Failure -> {
        errorSink.emit(result.error)
        null
      }
    }
  }
}

abstract class PythonMutableTargetAddInterpreterModel(params: PyInterpreterModelParams)
  : PythonAddInterpreterModel(params) {
  override val state: MutableTargetState = MutableTargetState(propertyGraph)

  override suspend fun initialize() {
    super.initialize()
    detectPoetryExecutable()
    detectPipEnvExecutable()
    detectUvExecutable()
  }

  suspend fun detectPoetryExecutable() {
    getPoetryExecutable().getOrNull()?.let {
      withContext(Dispatchers.EDT) {
        state.poetryExecutable.set(it.pathString)
      }
    }
  }

  suspend fun detectPipEnvExecutable() {
    getPipEnvExecutable().getOrNull()?.let {
      withContext(Dispatchers.EDT) {
        state.pipenvExecutable.set(it.pathString)
      }
    }
  }

  suspend fun detectUvExecutable() {
    getUvExecutable()?.pathString?.let {
      withContext(Dispatchers.EDT) {
        state.uvExecutable.set(it)
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
}


sealed class PythonSelectableInterpreter {
  /**
   * Base python is some system python (not venv) which can be used as a base for venv.
   * In terms of flavors we call it __not__ [PythonSdkFlavor.isPlatformIndependent]
   */
  open suspend fun isBasePython(): Boolean = withContext(Dispatchers.IO) {
    PythonSdkFlavor.tryDetectFlavorByLocalPath(homePath)?.isPlatformIndependent == false
  }

  abstract val homePath: String
  abstract val languageLevel: LanguageLevel
  open val uiCustomization: UICustomization? = null
  override fun toString(): String =
    "PythonSelectableInterpreter(homePath='$homePath')"
}

class ExistingSelectableInterpreter(val sdk: Sdk, override val languageLevel: LanguageLevel, val isSystemWide: Boolean) : PythonSelectableInterpreter() {
  override suspend fun isBasePython(): Boolean = withContext(Dispatchers.IO) {
    !sdk.sdkFlavor.isPlatformIndependent
  }

  override val homePath = sdk.homePath!! // todo is it safe
}

/**
 * [isBase] is a system interpreter, see [isBasePython]
 */
class DetectedSelectableInterpreter(override val homePath: String, override val languageLevel: LanguageLevel, private val isBase: Boolean, override val uiCustomization: UICustomization? = null) : PythonSelectableInterpreter(), Comparable<DetectedSelectableInterpreter> {
  override suspend fun isBasePython(): Boolean = isBase


  override fun compareTo(other: DetectedSelectableInterpreter): Int {
    // First by type
    val byType = (uiCustomization?.title ?: "").compareTo(other.uiCustomization?.title ?: "")
    // Then from the highest python to the lowest
    return if (byType != 0) byType else (languageLevel.compareTo(other.languageLevel) * -1)
  }
}

class ManuallyAddedSelectableInterpreter(override val homePath: String, override val languageLevel: LanguageLevel) : PythonSelectableInterpreter()

class InstallableSelectableInterpreter(val sdk: PySdkToInstall) : PythonSelectableInterpreter() {
  override suspend fun isBasePython(): Boolean = true
  override val homePath: String = ""
  override val languageLevel = PySdkUtil.getLanguageLevelForSdk(sdk)
}


open class AddInterpreterState(propertyGraph: PropertyGraph) {
  val selectedInterpreter: ObservableMutableProperty<PythonSelectableInterpreter?> = propertyGraph.property(null)
  val condaExecutable: ObservableMutableProperty<String> = propertyGraph.property("")

  /**
   * Use [PythonAddInterpreterModel.getBaseCondaOrError]
   */
  val selectedCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)

  /**
   * Use [PythonAddInterpreterModel.getBaseCondaOrError]
   */
  val baseCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)
}

class MutableTargetState(propertyGraph: PropertyGraph) : AddInterpreterState(propertyGraph) {
  val baseInterpreter: ObservableMutableProperty<PythonSelectableInterpreter?> = propertyGraph.property(null)
  val newCondaEnvName: ObservableMutableProperty<String> = propertyGraph.property("")
  val poetryExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val uvExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val pipenvExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val venvPath: ObservableMutableProperty<String> = propertyGraph.property("")
  val inheritSitePackages = propertyGraph.property(false)
  val makeAvailable = propertyGraph.property(false)
}


internal val PythonAddInterpreterModel.existingSdks
  get() = allInterpreters.value.filterIsInstance<ExistingSelectableInterpreter>().map { it.sdk }

internal fun PythonAddInterpreterModel.findInterpreter(path: String): PythonSelectableInterpreter? {
  return allInterpreters.value.asSequence().find { it.homePath == path }
}

internal suspend fun PythonAddInterpreterModel.detectCondaEnvironmentsOrError(errorSink: ErrorSink) {
  detectCondaEnvironments()?.let {
    errorSink.emit(it)
  }
}

internal suspend fun PythonAddInterpreterModel.getBaseCondaOrError(): Result<PyCondaEnv> {
  var baseConda = state.baseCondaEnv.get()
  if (baseConda != null) return Result.success(baseConda)
  detectCondaEnvironments()?.let { return failure(it) }
  baseConda = state.baseCondaEnv.get()
  return if (baseConda != null) Result.success(baseConda) else failure(message("python.sdk.conda.no.base.env.error"))
}