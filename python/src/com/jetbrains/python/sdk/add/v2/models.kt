// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.community.services.internal.impl.VanillaPythonWithLanguageLevelImpl
import com.intellij.python.community.services.shared.*
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.onFailure
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.v2.conda.toExecutor
import com.jetbrains.python.sdk.add.v2.hatch.HatchUIError
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.pipenv.getPipEnvExecutable
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

private val LOG: Logger = fileLogger()

@OptIn(ExperimentalCoroutinesApi::class)
abstract class PythonAddInterpreterModel(
  val projectPathFlows: ProjectPathFlows,
  private val systemPythonService: SystemPythonService = SystemPythonService(),
) {
  val modificationCounter: AtomicProperty<Int> = AtomicProperty(0)

  val propertyGraph: PropertyGraph = PropertyGraph()
  val navigator: PythonNewEnvironmentDialogNavigator = PythonNewEnvironmentDialogNavigator()
  open val state: AddInterpreterState = AddInterpreterState(propertyGraph)
  val targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null

  internal val knownInterpreters: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  private val _detectedInterpreters: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  val detectedInterpreters: StateFlow<List<PythonSelectableInterpreter>> = _detectedInterpreters
  val manuallyAddedInterpreters: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  private var installable: List<PythonSelectableInterpreter> = emptyList()
  val condaEnvironments: MutableStateFlow<List<PyCondaEnv>> = MutableStateFlow(emptyList())
  val hatchEnvironmentsResult: MutableStateFlow<PyResult<List<HatchVirtualEnvironment>>?> = MutableStateFlow(null)

  lateinit var allInterpreters: StateFlow<List<PythonSelectableInterpreter>>
  lateinit var baseInterpreters: StateFlow<List<PythonSelectableInterpreter>>

  val interpreterLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
  val condaEnvironmentsLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)

  @TestOnly
  @ApiStatus.Internal
  fun addDetected(detected: DetectedSelectableInterpreter) {
    _detectedInterpreters.value += detected
  }

  // If the project is provided, sdks associated with it will be kept in the list of interpreters. If not, then they will be filtered out.
  open fun initialize(scope: CoroutineScope) {
    merge(
      projectPathFlows.projectPathWithDefault,
      knownInterpreters,
      detectedInterpreters,
      manuallyAddedInterpreters,
      condaEnvironments,
      hatchEnvironmentsResult,
    ).map {
      modificationCounter.updateAndGet { it + 1 }
    }.launchIn(scope + Dispatchers.EDT)

    scope.launch(CoroutineName("Loading Interpreter List") + Dispatchers.EDT) {
      val existingSelectableInterpreters = getExistingSelectableInterpreters()
      val detectedSelectableInterpreters = getDetectedSelectableInterpreters(existingSelectableInterpreters)
      knownInterpreters.value = existingSelectableInterpreters
      _detectedInterpreters.value = detectedSelectableInterpreters
      installable = getExistingInstallableInterpreters(existingSelectableInterpreters, detectedSelectableInterpreters)
    }.invokeOnCompletion {
      this.interpreterLoading.value = false
    }

    this.allInterpreters = combine(
      knownInterpreters,
      detectedInterpreters,
      manuallyAddedInterpreters,
    ) { known, detected, added ->
      added + known + detected
    }.map { it.distinctBy { int -> int.homePath }.sorted() }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    this.baseInterpreters = allInterpreters.map { all ->
      all.filter { it.isBasePython() }
    }.mapLatest { all ->
      all.filter { it !is ExistingSelectableInterpreter || it.isSystemWide } + installable
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = emptyList())


    scope.launch(CoroutineName("Detecting Conda Executable and environments") + Dispatchers.IO) {
      detectCondaExecutable()
      detectCondaEnvironments()
    }.invokeOnCompletion {
      this.condaEnvironmentsLoading.value = false
    }
  }

  suspend fun detectCondaExecutable() {
    withContext(Dispatchers.IO) {
      val executor = targetEnvironmentConfiguration.toExecutor()
      val suggestedCondaPath = runCatching {
        suggestCondaPath(targetCommandExecutor = executor)
      }.getOrLogException(LOG)
      val suggestedCondaLocalPath = suggestedCondaPath?.toLocalPathOn(targetEnvironmentConfiguration)
      withContext(Dispatchers.EDT) {
        state.condaExecutable.set(suggestedCondaLocalPath?.toString().orEmpty())
      }
    }
  }

  /**
   * Returns error or `null` if no error
   */
  suspend fun detectCondaEnvironments(): PyResult<Unit> = withContext(Dispatchers.IO) {
    val fullCondaPathOnTarget = state.condaExecutable.get()
    if (fullCondaPathOnTarget.isBlank()) return@withContext PyResult.localizedError(message("python.sdk.conda.no.exec"))
    val environments = PyCondaEnv.getEnvs(fullCondaPathOnTarget).getOr { return@withContext it }
    val baseConda = environments.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }

    withContext(Dispatchers.EDT) {
      condaEnvironments.value = environments
      state.baseCondaEnv.set(baseConda)
    }
    return@withContext PyResult.success(Unit)
  }

  suspend fun detectHatchEnvironments(hatchExecutablePathString: String): PyResult<List<HatchVirtualEnvironment>> = withContext(Dispatchers.IO) {
    val projectPath = projectPathFlows.projectPathWithDefault.first()
    val hatchExecutablePath = NioFiles.toPath(hatchExecutablePathString)
                              ?: return@withContext Result.failure(
                                HatchUIError.HatchExecutablePathIsNotValid(hatchExecutablePathString))
    val hatchWorkingDirectory = if (projectPath.isDirectory()) projectPath else projectPath.parent
    val hatchService = hatchWorkingDirectory.getHatchService(hatchExecutablePath).getOr { return@withContext it }

    val hatchEnvironments = hatchService.findVirtualEnvironments().getOr { return@withContext it }
    val availableEnvironments = when {
      hatchWorkingDirectory == projectPath -> hatchEnvironments
      else -> HatchVirtualEnvironment.AVAILABLE_ENVIRONMENTS_FOR_NEW_PROJECT
    }
    success(availableEnvironments)
  }

  private suspend fun getExistingSelectableInterpreters(): List<ExistingSelectableInterpreter> = withContext(Dispatchers.IO) {
    val projectPathPrefix: String = projectPathFlows.projectPathWithDefault.first().toString()
    val allValidSdks = PythonSdkUtil
      .getAllSdks()
      .filter { sdk ->
        val associatedModulePath = sdk.associatedModulePath ?: return@filter true
        associatedModulePath.startsWith(projectPathPrefix)
      }
      .map {
        ExistingSelectableInterpreter(
          it,
          PySdkUtil.getLanguageLevelForSdk(it),
          it.isSystemWide)
      }
    allValidSdks
  }

  private suspend fun getExistingInstallableInterpreters(
    existingSelectableInterpreters: List<ExistingSelectableInterpreter>,
    detectedSelectableInterpreters: List<DetectedSelectableInterpreter>,
  ): List<InstallableSelectableInterpreter> = withContext(Dispatchers.IO) {
    val languageLevels = existingSelectableInterpreters.map { it.languageLevel }.toSet() +
                         detectedSelectableInterpreters.map { it.languageLevel }.toSet()

    val filteredInstallable: List<InstallableSelectableInterpreter> = getSdksToInstall().mapNotNull {
      LanguageLevel.fromPythonVersionSafe(it.installation.release.version)?.let { languageLevel ->
        languageLevel to it
      }
    }
      .filter { it.first !in languageLevels }
      .sortedByDescending { it.first }
      .map { InstallableSelectableInterpreter(it.second) }
    filteredInstallable
  }


  private suspend fun getDetectedSelectableInterpreters(existingSelectableInterpreters: List<ExistingSelectableInterpreter>): List<DetectedSelectableInterpreter> = withContext(Dispatchers.IO) {
    val existingSdkPaths = existingSelectableInterpreters.mapNotNull { tryResolvePath(it.homePath) }.toSet()

    // Venvs are not detected manually, but must migrate to VenvService or so
    val venvs: List<VanillaPythonWithLanguageLevel> = VanillaPythonWithLanguageLevelImpl.createByPythonBinaries(
      VirtualEnvSdkFlavor.getInstance().suggestLocalHomePaths(null, null)).mapNotNull { (venv, r) ->
      when (r) {
        is Result.Failure -> {
          fileLogger().warn("Skipping $venv : ${r.error}")
          null
        }
        is Result.Success -> r.result
      }
    }

    // System (base) pythons
    val system: List<SystemPython> = systemPythonService.findSystemPythons()

    // Python + isBase. Both: system and venv.
    val detected: List<DetectedSelectableInterpreter> =
      (
        venvs.map { Triple(it, false, null) } +
        system.map { Triple(it, true, it.ui) }
      )
        .filterNot { (python, _) -> python.pythonBinary in existingSdkPaths }
        .map { (python, base, ui) -> DetectedSelectableInterpreter(python.pythonBinary.pathString, python.languageLevel, base, ui) }
        .sorted()

    detected
  }

  private fun addManuallyAddedInterpreter(interpreter: PythonSelectableInterpreter) {
    manuallyAddedInterpreters.value += interpreter
    state.selectedInterpreter.set(interpreter)
  }

  internal fun addInterpreter(python: VanillaPythonWithLanguageLevel): PythonSelectableInterpreter {
    val interpreter = ManuallyAddedSelectableInterpreter(python).also { addManuallyAddedInterpreter(it) }
    return interpreter
  }

  internal fun addInterpreter(path: String): PythonSelectableInterpreter {
    val languageLevel = PySdkUtil.getLanguageLevelForSdk(PythonSdkUtil.findSdkByKey(path))
    val interpreter = ManuallyAddedSelectableInterpreter(path, languageLevel).also { addManuallyAddedInterpreter(it) }
    return interpreter
  }

  open fun addInterpreter(sdk: Sdk) {
    val interpreter = ExistingSelectableInterpreter(sdk, PySdkUtil.getLanguageLevelForSdk(sdk), sdk.isSystemWide)
    addManuallyAddedInterpreter(interpreter)
  }

  @RequiresEdt
  internal fun addInstalledInterpreter(homePath: Path, languageLevel: LanguageLevel): DetectedSelectableInterpreter {
    val installedInterpreter = DetectedSelectableInterpreter(homePath.pathString, languageLevel, true)
    _detectedInterpreters.value += installedInterpreter
    return installedInterpreter
  }

  /**
   * Given [pathToPython] returns either cleaned path (if valid) or null and reports error to [errorSink]
   */
  suspend fun getSystemPythonFromSelection(pathToPython: String, errorSink: ErrorSink): SystemPython? {
    val result = try {
      when (val r = systemPythonService.registerSystemPython(Path(pathToPython))) {
        is Result.Failure -> PyResult.failure(r.error)
        is Result.Success -> PyResult.success(r.result)
      }
    }
    catch (e: InvalidPathException) {
      PyResult.localizedError(e.localizedMessage)
    }

    return when (result) {
      is Result.Success -> result.result
      is Result.Failure -> {
        errorSink.emit(result.error)
        null
      }
    }
  }
}

abstract class PythonMutableTargetAddInterpreterModel(projectPathFlows: ProjectPathFlows) : PythonAddInterpreterModel(projectPathFlows) {
  override val state: MutableTargetState = MutableTargetState(propertyGraph)

  override fun initialize(scope: CoroutineScope) {
    super.initialize(scope)
    scope.launch(CoroutineName("Detecting Poetry Executable")) {
      detectPoetryExecutable()
    }
    scope.launch(CoroutineName("Detecting Pip Executable")) {
      detectPipEnvExecutable()
    }
    scope.launch(CoroutineName("Detecting uv Executable")) {
      detectUvExecutable()
    }
    scope.launch(CoroutineName("Detecting Hatch Executable")) {
      detectHatchExecutable()
    }

    state.hatchExecutable.afterChange(scope.asDisposable()) { pathString ->
      hatchEnvironmentsResult.value = null
      scope.launch(CoroutineName("Detecting Hatch Environments")) {
        val hatchEnvironments = detectHatchEnvironments(pathString)
        withContext(Dispatchers.EDT) {
          hatchEnvironmentsResult.value = hatchEnvironments
        }
      }
    }
  }

  suspend fun detectPoetryExecutable(): Unit = withContext(Dispatchers.IO) {
    getPoetryExecutable().getOrNull()?.let {
      withContext(Dispatchers.EDT) {
        state.poetryExecutable.set(it.pathString)
      }
    }
  }

  suspend fun detectPipEnvExecutable(): Unit = withContext(Dispatchers.IO) {
    getPipEnvExecutable().getOrNull()?.let {
      withContext(Dispatchers.EDT) {
        state.pipenvExecutable.set(it.pathString)
      }
    }
  }

  suspend fun detectUvExecutable(): Unit = withContext(Dispatchers.IO) {
    getUvExecutable()?.pathString?.let {
      withContext(Dispatchers.EDT) {
        state.uvExecutable.set(it)
      }
    }
  }

  suspend fun detectHatchExecutable(): Unit = withContext(Dispatchers.IO) {
    HatchConfiguration.getOrDetectHatchExecutablePath().getOrNull()?.pathString?.let {
      withContext(Dispatchers.EDT) {
        state.hatchExecutable.set(it)
      }
    }
  }
}

class PythonLocalAddInterpreterModel(projectPathFlows: ProjectPathFlows) : PythonMutableTargetAddInterpreterModel(projectPathFlows) {
  override fun initialize(scope: CoroutineScope) {
    super.initialize(scope)

    val mostRecentlyUsedBasePath = PySdkSettings.instance.preferredVirtualEnvBaseSdk
    val interpreterToSelect = detectedInterpreters.value.find { it.homePath == mostRecentlyUsedBasePath }
                              ?: baseInterpreters.value.filterIsInstance<ExistingSelectableInterpreter>().maxByOrNull { it.languageLevel }

    if (interpreterToSelect != null) {
      state.baseInterpreter.set(interpreterToSelect)
    }
  }
}


sealed class PythonSelectableInterpreter : Comparable<PythonSelectableInterpreter>, UiHolder, LanguageLevelHolder {
  companion object {
    private val comparator = LanguageLevelWithUiComparator<PythonSelectableInterpreter>()
  }

  /**
   * Base python is some system python (not venv) which can be used as a base for venv.
   * In terms of flavors we call it __not__ [PythonSdkFlavor.isPlatformIndependent]
   */
  open suspend fun isBasePython(): Boolean = withContext(Dispatchers.IO) {
    PythonSdkFlavor.tryDetectFlavorByLocalPath(homePath)?.isPlatformIndependent == false
  }

  abstract val homePath: String
  abstract override val languageLevel: LanguageLevel
  override val ui: UICustomization? = null
  override fun toString(): String = "PythonSelectableInterpreter(homePath='$homePath')"

  override fun compareTo(other: PythonSelectableInterpreter): Int =
    comparator.compare(this, other)
}

class ExistingSelectableInterpreter(
  val sdk: Sdk,
  override val languageLevel: LanguageLevel,
  val isSystemWide: Boolean,
) : PythonSelectableInterpreter() {
  override suspend fun isBasePython(): Boolean = withContext(Dispatchers.IO) {
    !sdk.sdkFlavor.isPlatformIndependent
  }

  override fun toString(): String {
    return "ExistingSelectableInterpreter(sdk=$sdk, languageLevel=$languageLevel, isSystemWide=$isSystemWide, homePath='$homePath')"
  }

  override val homePath = sdk.homePath!! // todo is it safe


}

/**
 * [isBase] is a system interpreter, see [isBasePython]
 */
class DetectedSelectableInterpreter(
  override val homePath: String,
  override val languageLevel: LanguageLevel,
  private val isBase: Boolean,
  override val ui: UICustomization? = null,
) : PythonSelectableInterpreter() {
  override suspend fun isBasePython(): Boolean = isBase
  override fun toString(): String {
    return "DetectedSelectableInterpreter(homePath='$homePath', languageLevel=$languageLevel, isBase=$isBase, uiCustomization=$ui)"
  }

}

class ManuallyAddedSelectableInterpreter(
  override val homePath: String,
  override val languageLevel: LanguageLevel,
) : PythonSelectableInterpreter() {
  constructor(python: VanillaPythonWithLanguageLevel) : this(python.pythonBinary.pathString, python.languageLevel)

  override fun toString(): String {
    return "ManuallyAddedSelectableInterpreter(homePath='$homePath', languageLevel=$languageLevel)"
  }


}

class InstallableSelectableInterpreter(val sdk: PySdkToInstall) : PythonSelectableInterpreter() {
  override suspend fun isBasePython(): Boolean = true
  override val homePath: String = ""
  override val languageLevel: LanguageLevel = PySdkUtil.getLanguageLevelForSdk(sdk)
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

  val selectedHatchEnv: ObservableMutableProperty<HatchVirtualEnvironment?> = propertyGraph.property(null)
}

class MutableTargetState(propertyGraph: PropertyGraph) : AddInterpreterState(propertyGraph) {
  val baseInterpreter: ObservableMutableProperty<PythonSelectableInterpreter?> = propertyGraph.property(null)
  val newCondaEnvName: ObservableMutableProperty<String> = propertyGraph.property("")
  val poetryExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val uvExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val hatchExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val pipenvExecutable: ObservableMutableProperty<String> = propertyGraph.property("")
  val venvPath: ObservableMutableProperty<String> = propertyGraph.property("")
  val inheritSitePackages: GraphProperty<Boolean> = propertyGraph.property(false)

  /**
   * Associate SDK with particular module (if true)
   */
  val makeAvailableForAllProjects: GraphProperty<Boolean> = propertyGraph.property(false)
}


internal val PythonAddInterpreterModel.existingSdks
  get() = allInterpreters.value.filterIsInstance<ExistingSelectableInterpreter>().map { it.sdk }

internal fun PythonAddInterpreterModel.findInterpreter(path: String): PythonSelectableInterpreter? {
  return allInterpreters.value.find { it.homePath == path }
}

internal suspend fun PythonAddInterpreterModel.detectCondaEnvironmentsOrError(errorSink: ErrorSink) {
  detectCondaEnvironments().onFailure {
    errorSink.emit(it)
  }
}

internal suspend fun PythonAddInterpreterModel.getBaseCondaOrError(): PyResult<PyCondaEnv> {
  var baseConda = state.baseCondaEnv.get()
  if (baseConda != null) return PyResult.success(baseConda)
  detectCondaEnvironments().getOr { return it }
  baseConda = state.baseCondaEnv.get()
  return if (baseConda != null) PyResult.success(baseConda) else PyResult.localizedError(message("python.sdk.conda.no.base.env.error"))
}

internal suspend fun PythonAddInterpreterModel.getBasePath(module: Module?): Path = withContext(Dispatchers.IO) {
  val pyProjectTomlBased = module?.let { PyProjectToml.findFile(it)?.toNioPathOrNull()?.parent }

  pyProjectTomlBased
  ?: module?.basePath?.let { Path.of(it) }
  ?: projectPathFlows.projectPathWithDefault.first()
}
