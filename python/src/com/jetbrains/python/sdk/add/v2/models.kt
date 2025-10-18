// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.services.shared.*
import com.intellij.python.hatch.HatchConfiguration.getOrDetectHatchExecutablePath
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.*
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.Result
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.v2.conda.detectCondaEnvironments
import com.jetbrains.python.sdk.add.v2.conda.detectCondaExecutable
import com.jetbrains.python.sdk.add.v2.hatch.HatchUIError
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.pipenv.getPipEnvExecutable
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

@OptIn(ExperimentalCoroutinesApi::class)
abstract class PythonAddInterpreterModel<P : PathHolder>(
  val projectPathFlows: ProjectPathFlows,
  val fileSystem: FileSystem<P>,
) {
  val modificationCounter: AtomicProperty<Int> = AtomicProperty(0)

  val propertyGraph: PropertyGraph = PropertyGraph()
  val navigator: PythonNewEnvironmentDialogNavigator = PythonNewEnvironmentDialogNavigator()
  open val state: AddInterpreterState<P> = AddInterpreterState(propertyGraph)

  internal val knownInterpreters: MutableStateFlow<List<PythonSelectableInterpreter<P>>?> = MutableStateFlow(null)
  private val _detectedInterpreters: MutableStateFlow<List<DetectedSelectableInterpreter<P>>?> = MutableStateFlow(null)
  val detectedInterpreters: StateFlow<List<DetectedSelectableInterpreter<P>>?> = _detectedInterpreters
  val manuallyAddedInterpreters: MutableStateFlow<List<PythonSelectableInterpreter<P>>> = MutableStateFlow(emptyList())
  private var installable: List<InstallableSelectableInterpreter<P>> = emptyList()
  val condaEnvironments: MutableStateFlow<List<PyCondaEnv>> = MutableStateFlow(emptyList())
  val hatchEnvironmentsResult: MutableStateFlow<PyResult<List<HatchVirtualEnvironment>>?> = MutableStateFlow(null)

  lateinit var allInterpreters: StateFlow<List<PythonSelectableInterpreter<P>>?>
  lateinit var baseInterpreters: StateFlow<List<PythonSelectableInterpreter<P>>?>

  val condaEnvironmentsLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)

  @TestOnly
  @ApiStatus.Internal
  fun addDetected(detected: DetectedSelectableInterpreter<P>) {
    _detectedInterpreters.value?.let { existing ->
      _detectedInterpreters.value = existing + detected
    }
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

    scope.launch(TraceContext(message("tracecontext.loading.interpreter.list"), scope) + Dispatchers.EDT) {
      installable = fileSystem.getInstallableInterpreters()
      val projectPathPrefix = projectPathFlows.projectPathWithDefault.first()
      val existingSelectableInterpreters = fileSystem.getExistingSelectableInterpreters(projectPathPrefix)
      knownInterpreters.value = existingSelectableInterpreters
      _detectedInterpreters.value = fileSystem.getDetectedSelectableInterpreters(existingSelectableInterpreters)
    }

    this.allInterpreters = combine(
      knownInterpreters,
      detectedInterpreters,
      manuallyAddedInterpreters,
    ) { known, detected, added ->
      if (known == null || detected == null) return@combine null
      added + known + detected
    }.map { all ->
      all?.distinctBy { int -> int.homePath }?.sorted()
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = null)

    this.baseInterpreters = combine(
      detectedInterpreters,
      manuallyAddedInterpreters
    ) { detected, manual ->
      val base = detected?.filter { it.isBase } ?: return@combine null
      val existingLanguageLevels = base.map { it.languageLevel }.toSet()
      val nonExistingInstallable = installable.filter { it.languageLevel !in existingLanguageLevels }
      manual + base.sorted() + nonExistingInstallable
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = null)


    scope.launch(TraceContext(message("tracecontext.detecting.conda.executable.and.environments"), scope) + Dispatchers.IO) {
      detectCondaExecutable()
      detectCondaEnvironments()
    }.invokeOnCompletion {
      this.condaEnvironmentsLoading.value = false
    }
  }

  suspend fun detectHatchEnvironments(hatchExecutable: BinaryToExec): PyResult<List<HatchVirtualEnvironment>> = withContext(Dispatchers.IO) {
    val projectPath = projectPathFlows.projectPathWithDefault.first()
    val hatchExecutablePath = (hatchExecutable as? BinOnEel)?.path
                              ?: return@withContext Result.failure(HatchUIError.HatchExecutablePathIsNotValid(hatchExecutable.toString()))
    val hatchWorkingDirectory = if (projectPath.isDirectory()) projectPath else projectPath.parent
    val hatchService = hatchWorkingDirectory.getHatchService(hatchExecutablePath).getOr { return@withContext it }

    val hatchEnvironments = hatchService.findVirtualEnvironments().getOr { return@withContext it }
    val availableEnvironments = when {
      hatchWorkingDirectory == projectPath -> hatchEnvironments
      else -> HatchVirtualEnvironment.AVAILABLE_ENVIRONMENTS_FOR_NEW_PROJECT
    }
    success(availableEnvironments)
  }


  internal fun addManuallyAddedInterpreter(interpreter: PythonSelectableInterpreter<P>) {
    manuallyAddedInterpreters.value += interpreter
    state.selectedInterpreter.set(interpreter)
  }

  internal suspend fun addManuallyAddedInterpreter(homePath: P): PyResult<ManuallyAddedSelectableInterpreter<P>> {
    val python = homePath.let { fileSystem.getSystemPythonFromSelection(it) }.getOr { return it }

    val interpreter = ManuallyAddedSelectableInterpreter(homePath, python.languageLevel).also {
      this@PythonAddInterpreterModel.addManuallyAddedInterpreter(it)
    }
    return PyResult.success(interpreter)
  }

  open fun addInterpreter(sdk: Sdk) {
    val interpreter = ExistingSelectableInterpreter(
      fileSystem.wrapSdk(sdk),
      PySdkUtil.getLanguageLevelForSdk(sdk),
      sdk.isSystemWide
    )
    this@PythonAddInterpreterModel.addManuallyAddedInterpreter(interpreter)
  }

  @RequiresEdt
  internal fun addInstalledInterpreter(homePath: P, languageLevel: LanguageLevel): DetectedSelectableInterpreter<P> {
    val installedInterpreter = DetectedSelectableInterpreter(homePath, languageLevel, true)
    _detectedInterpreters.value = (_detectedInterpreters.value ?: emptyList()) + installedInterpreter
    return installedInterpreter
  }
}

abstract class PythonMutableTargetAddInterpreterModel<P : PathHolder>(projectPathFlows: ProjectPathFlows, fileSystem: FileSystem<P>) : PythonAddInterpreterModel<P>(projectPathFlows, fileSystem) {
  override val state: MutableTargetState<P> = MutableTargetState(propertyGraph)

  override fun initialize(scope: CoroutineScope) {
    super.initialize(scope)
    scope.launch(TraceContext(message("tracecontext.detecting.poetry.executable"), scope)) {
      detectPoetryExecutable()
    }
    scope.launch(TraceContext(message("tracecontext.detecting.pip.executable"), scope)) {
      detectPipEnvExecutable()
    }
    scope.launch(TraceContext(message("tracecontext.detecting.uv.executable"), scope)) {
      detectUvExecutable()
    }
    scope.launch(TraceContext(message("tracecontext.detecting.hatch.executable"), scope)) {
      detectHatchExecutable()
    }
  }

  suspend fun detectPoetryExecutable() {
    if ((fileSystem as? FileSystem.Eel)?.eelApi != localEel) return // getPoetryExecutable() works only with localEel currently

    state.poetryExecutable.autodetectWithVersionProbe("poetry") {
      getPoetryExecutable().getOrNull()?.let { PathHolder.Eel(it) } as P?
    }
  }

  suspend fun detectPipEnvExecutable() {
    if ((fileSystem as? FileSystem.Eel)?.eelApi != localEel) return // getPipEnvExecutable() works only with localEel currently

    state.pipenvExecutable.autodetectWithVersionProbe("pipenv") {
      getPipEnvExecutable().getOrNull()?.let { PathHolder.Eel(it) } as P?
    }
  }

  suspend fun detectUvExecutable() {
    if ((fileSystem as? FileSystem.Eel)?.eelApi != localEel) return // getUvExecutable() works only with localEel currently

    state.uvExecutable.autodetectWithVersionProbe("uv") {
      getUvExecutable()?.let { PathHolder.Eel(it) } as P?
    }
  }

  suspend fun detectHatchExecutable() {
    if (fileSystem !is FileSystem.Eel) return // getOrDetectHatchExecutablePath() works only with eel filesystem currently

    state.hatchExecutable.autodetectWithVersionProbe("hatch") {
      getOrDetectHatchExecutablePath(fileSystem.eelApi).getOrNull()?.let { PathHolder.Eel(it) } as P?
    }
  }

  private suspend fun ObservableMutableProperty<ValidatedPath.Executable<P>?>.autodetectWithVersionProbe(
    toolVersionPrefix: String,
    toolPathSupplier: suspend () -> P?,
  ): Unit = withContext(Dispatchers.IO) {
    val path = toolPathSupplier.invoke()
    val validatedPath = path?.validateToolExecutableByVersionProbe(toolVersionPrefix)
                        ?: ValidatedPath.Executable(
                          pathHolder = path,
                          validationResult = PyResult.localizedError(message("python.sdk.executable.is.not.detected"))
                        )

    withContext(Dispatchers.EDT) {
      set(validatedPath)
    }
  }

  private suspend fun P.validateToolExecutableByVersionProbe(toolVersionPrefix: String): ValidatedPath.Executable<P> {
    val binaryToExec = fileSystem.getBinaryToExec(this)
    val validationResult = binaryToExec.getToolVersion(toolVersionPrefix)
    return ValidatedPath.Executable(
      pathHolder = this,
      validationResult = validationResult
    )
  }
}

class PythonLocalAddInterpreterModel<P : PathHolder>(projectPathFlows: ProjectPathFlows, fileSystem: FileSystem<P>) : PythonMutableTargetAddInterpreterModel<P>(projectPathFlows, fileSystem) {
  override fun initialize(scope: CoroutineScope) {
    super.initialize(scope)

    val preferredInterpreterBasePath = fileSystem.preferredInterpreterBasePath()

    val interpreterToSelect = preferredInterpreterBasePath?.let { path ->
      detectedInterpreters.value?.find { it.homePath == path }
    } ?: baseInterpreters.value?.filterIsInstance<ExistingSelectableInterpreter<P>>()?.maxByOrNull { it.languageLevel }

    if (interpreterToSelect != null) {
      state.baseInterpreter.set(interpreterToSelect)
    }
  }
}


sealed class PythonSelectableInterpreter<P : PathHolder> : Comparable<PythonSelectableInterpreter<*>>, UiHolder, LanguageLevelHolder {
  companion object {
    private val comparator = LanguageLevelWithUiComparator<PythonSelectableInterpreter<*>>()
  }

  abstract val homePath: P?
  abstract override val languageLevel: LanguageLevel
  override val ui: PyToolUIInfo? = null
  override fun toString(): String = "PythonSelectableInterpreter(homePath='$homePath')"

  override fun compareTo(other: PythonSelectableInterpreter<*>): Int = comparator.compare(this, other)
}

class ExistingSelectableInterpreter<P : PathHolder>(
  val sdkWrapper: SdkWrapper<P>,
  override val languageLevel: LanguageLevel,
  val isSystemWide: Boolean,
) : PythonSelectableInterpreter<P>() {
  override val homePath: P
    get() = sdkWrapper.homePath

  override fun toString(): String {
    return "ExistingSelectableInterpreter(sdk=${sdkWrapper.sdk}, languageLevel=$languageLevel, isSystemWide=$isSystemWide, homePath='$homePath')"
  }
}

/**
 * [isBase] is a system interpreter, see [isBasePython]
 */
class DetectedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val languageLevel: LanguageLevel,
  val isBase: Boolean,
  override val ui: PyToolUIInfo? = null,
) : PythonSelectableInterpreter<P>() {
  override fun toString(): String {
    return "DetectedSelectableInterpreter(homePath='$homePath', languageLevel=$languageLevel, isBase=$isBase, uiCustomization=$ui)"
  }
}

class ManuallyAddedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val languageLevel: LanguageLevel,
) : PythonSelectableInterpreter<P>() {
  override fun toString(): String {
    return "ManuallyAddedSelectableInterpreter(homePath='$homePath', languageLevel=$languageLevel)"
  }
}

class InstallableSelectableInterpreter<P : PathHolder>(
  override val languageLevel: LanguageLevel,
  val sdk: PySdkToInstall,
) : PythonSelectableInterpreter<P>() {
  override val homePath: P? = null
}

sealed interface PathHolder {
  data class Eel(val path: Path) : PathHolder {
    override fun toString(): String {
      return path.pathString
    }
  }

  data class Target(val pathString: FullPathOnTarget) : PathHolder {
    override fun toString(): String {
      return pathString
    }
  }
}

sealed interface ValidatedPath<T, P : PathHolder> {
  val pathHolder: P?
  val validationResult: PyResult<T>

  data class Folder<P : PathHolder>(
    override val pathHolder: P?,
    override val validationResult: PyResult<Unit>,
  ) : ValidatedPath<Unit, P>

  data class Executable<P : PathHolder>(
    override val pathHolder: P?,
    override val validationResult: PyResult<Version>,
  ) : ValidatedPath<Version, P>
}

open class AddInterpreterState<P : PathHolder>(propertyGraph: PropertyGraph) {
  val selectedInterpreter: ObservableMutableProperty<PythonSelectableInterpreter<P>?> = propertyGraph.property(null)
  val condaExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)

  /**
   * Use [PythonAddInterpreterModel.getBaseCondaOrError]
   */
  val selectedCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)

  /**
   * Use [PythonAddInterpreterModel.getBaseCondaOrError]
   */
  val baseCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)

  val selectedHatchEnv: ObservableMutableProperty<HatchVirtualEnvironment?> = propertyGraph.property(null)

  val targetPanelExtension: ObservableMutableProperty<TargetPanelExtension?> = propertyGraph.property(null)
}

class MutableTargetState<P : PathHolder>(propertyGraph: PropertyGraph) : AddInterpreterState<P>(propertyGraph) {
  val baseInterpreter: ObservableMutableProperty<PythonSelectableInterpreter<P>?> = propertyGraph.property(null)
  val newCondaEnvName: ObservableMutableProperty<String> = propertyGraph.property("")
  val poetryExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val uvExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val hatchExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val pipenvExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val venvPath: ObservableMutableProperty<ValidatedPath.Folder<P>?> = propertyGraph.property(null)
  val inheritSitePackages: GraphProperty<Boolean> = propertyGraph.property(false)

  /**
   * Associate SDK with particular module (if true)
   */
  val makeAvailableForAllProjects: GraphProperty<Boolean> = propertyGraph.property(false)
}


internal val <P : PathHolder> PythonAddInterpreterModel<P>.existingSdks: List<Sdk>
  get() = allInterpreters.value?.filterIsInstance<ExistingSelectableInterpreter<P>>()?.map { it.sdkWrapper.sdk } ?: emptyList()


internal suspend fun PythonAddInterpreterModel<*>.detectCondaEnvironmentsOrError(errorSink: ErrorSink) {
  detectCondaEnvironments().onFailure {
    errorSink.emit(it)
  }
}

internal suspend fun PythonAddInterpreterModel<*>.getBaseCondaOrError(): PyResult<PyCondaEnv> {
  var baseConda = state.baseCondaEnv.get()
  if (baseConda != null) return PyResult.success(baseConda)
  detectCondaEnvironments().getOr { return it }
  baseConda = state.baseCondaEnv.get()
  return if (baseConda != null) PyResult.success(baseConda) else PyResult.localizedError(message("python.sdk.conda.no.base.env.error"))
}

internal suspend fun PythonAddInterpreterModel<*>.getBasePath(module: Module?): Path = withContext(Dispatchers.IO) {
  val pyProjectTomlBased = module?.let { PyProjectToml.findFile(it)?.toNioPathOrNull()?.parent }

  pyProjectTomlBased ?: module?.basePath?.let { Path.of(it) } ?: projectPathFlows.projectPathWithDefault.first()
}
