// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.application.UI
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.community.services.shared.PythonInfoHolder
import com.intellij.python.community.services.shared.PythonInfoWithUiComparator
import com.intellij.python.community.services.shared.UiHolder
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.PySdkToInstall
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.add.v2.conda.CondaViewModel
import com.jetbrains.python.sdk.add.v2.hatch.HatchViewModel
import com.jetbrains.python.sdk.add.v2.pipenv.PipenvViewModel
import com.jetbrains.python.sdk.add.v2.poetry.PoetryViewModel
import com.jetbrains.python.sdk.add.v2.uv.UvViewModel
import com.jetbrains.python.sdk.add.v2.venv.VenvViewModel
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.isSystemWide
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.pathString

interface PythonToolViewModel {
  fun initialize(scope: CoroutineScope)
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class PythonAddInterpreterModel<P : PathHolder>(
  val projectPathFlows: ProjectPathFlows,
  val fileSystem: FileSystem<P>,
) {
  val modificationCounter: AtomicProperty<Int> = AtomicProperty(0)

  val propertyGraph: PropertyGraph = PropertyGraph()
  val navigator: PythonNewEnvironmentDialogNavigator = PythonNewEnvironmentDialogNavigator()
  open val state: AddInterpreterState<P> = AddInterpreterState(propertyGraph)

  val condaViewModel: CondaViewModel<P> = CondaViewModel(fileSystem, propertyGraph, projectPathFlows)
  val uvViewModel: UvViewModel<P> = UvViewModel(fileSystem, propertyGraph)
  val pipenvViewModel: PipenvViewModel<P> = PipenvViewModel(fileSystem, propertyGraph)
  val poetryViewModel: PoetryViewModel<P> = PoetryViewModel(fileSystem, propertyGraph)
  val hatchViewModel: HatchViewModel<P> = HatchViewModel(fileSystem, propertyGraph, projectPathFlows)
  val venvViewModel: VenvViewModel<P> = VenvViewModel(fileSystem, propertyGraph, projectPathFlows)

  internal val knownInterpreters: MutableStateFlow<List<PythonSelectableInterpreter<P>>?> = MutableStateFlow(null)
  private val _detectedInterpreters: MutableStateFlow<List<DetectedSelectableInterpreter<P>>?> = MutableStateFlow(null)
  val detectedInterpreters: StateFlow<List<DetectedSelectableInterpreter<P>>?> = _detectedInterpreters
  val manuallyAddedInterpreters: MutableStateFlow<List<PythonSelectableInterpreter<P>>> = MutableStateFlow(emptyList())
  private var installable: List<InstallableSelectableInterpreter<P>> = emptyList()
  lateinit var allInterpreters: StateFlow<List<PythonSelectableInterpreter<P>>?>
  lateinit var baseInterpreters: StateFlow<List<PythonSelectableInterpreter<P>>?>


  @TestOnly
  @ApiStatus.Internal
  fun addDetected(detected: DetectedSelectableInterpreter<P>) {
    _detectedInterpreters.value?.let { existing ->
      _detectedInterpreters.value = existing + detected
    }
  }

  // If the project is provided, sdks associated with it will be kept in the list of interpreters. If not, then they will be filtered out.
  open fun initialize(scope: CoroutineScope) {
    listOf(condaViewModel, uvViewModel, pipenvViewModel, poetryViewModel, hatchViewModel, venvViewModel).forEach { it.initialize(scope) }

    merge(
      projectPathFlows.projectPathWithDefault,
      knownInterpreters,
      detectedInterpreters,
      manuallyAddedInterpreters,
      condaViewModel.condaEnvironmentsResult,
      hatchViewModel.availableEnvironments,
    ).map {
      modificationCounter.updateAndGet { it + 1 }
    }.launchIn(scope + Dispatchers.UI)

    scope.launch(TraceContext(message("tracecontext.loading.interpreter.list"), scope) + Dispatchers.UI) {
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
      val existingLanguageLevels = base.map { it.pythonInfo.languageLevel }.toSet()
      val nonExistingInstallable = installable.filter { it.pythonInfo.languageLevel !in existingLanguageLevels }
      manual + base.sorted() + nonExistingInstallable
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = null)
  }


  internal fun addManuallyAddedInterpreter(interpreter: PythonSelectableInterpreter<P>) {
    manuallyAddedInterpreters.value += interpreter
    state.selectedInterpreter.set(interpreter)
  }

  internal suspend fun addManuallyAddedInterpreter(homePath: P): PyResult<ManuallyAddedSelectableInterpreter<P>> {
    val python = homePath.let { fileSystem.getSystemPythonFromSelection(it) }.getOr { return it }

    val interpreter = ManuallyAddedSelectableInterpreter(homePath, python.pythonInfo).also {
      this@PythonAddInterpreterModel.addManuallyAddedInterpreter(it)
    }
    return PyResult.success(interpreter)
  }

  open fun addInterpreter(sdk: Sdk) {
    val interpreter = ExistingSelectableInterpreter(
      fileSystem.wrapSdk(sdk),
      PythonInfo(PySdkUtil.getLanguageLevelForSdk(sdk)),
      sdk.isSystemWide
    )
    this@PythonAddInterpreterModel.addManuallyAddedInterpreter(interpreter)
  }

  @RequiresEdt
  internal fun addInstalledInterpreter(homePath: P, pythonInfo: PythonInfo): DetectedSelectableInterpreter<P> {
    val installedInterpreter = DetectedSelectableInterpreter(homePath, pythonInfo, true)
    _detectedInterpreters.value = (_detectedInterpreters.value ?: emptyList()) + installedInterpreter
    return installedInterpreter
  }
}

abstract class PythonMutableTargetAddInterpreterModel<P : PathHolder>(projectPathFlows: ProjectPathFlows, fileSystem: FileSystem<P>) : PythonAddInterpreterModel<P>(projectPathFlows, fileSystem) {
  override val state: MutableTargetState<P> = MutableTargetState(propertyGraph)
}

class PythonLocalAddInterpreterModel<P : PathHolder>(projectPathFlows: ProjectPathFlows, fileSystem: FileSystem<P>) : PythonMutableTargetAddInterpreterModel<P>(projectPathFlows, fileSystem) {
  override fun initialize(scope: CoroutineScope) {
    super.initialize(scope)

    val preferredInterpreterBasePath = fileSystem.preferredInterpreterBasePath()

    val interpreterToSelect = preferredInterpreterBasePath?.let { path ->
      detectedInterpreters.value?.find { it.homePath == path }
    } ?: baseInterpreters.value?.filterIsInstance<ExistingSelectableInterpreter<P>>()?.maxByOrNull { it.pythonInfo.languageLevel }

    if (interpreterToSelect != null) {
      state.baseInterpreter.set(interpreterToSelect)
    }
  }
}


sealed class PythonSelectableInterpreter<P : PathHolder> : Comparable<PythonSelectableInterpreter<*>>, UiHolder, PythonInfoHolder {
  companion object {
    private val comparator = PythonInfoWithUiComparator<PythonSelectableInterpreter<*>>()
  }

  abstract val homePath: P?
  abstract override val pythonInfo: PythonInfo
  override val ui: PyToolUIInfo? = null
  override fun toString(): String = "PythonSelectableInterpreter(homePath='$homePath')"

  override fun compareTo(other: PythonSelectableInterpreter<*>): Int = comparator.compare(this, other)
}

class ExistingSelectableInterpreter<P : PathHolder>(
  val sdkWrapper: SdkWrapper<P>,
  override val pythonInfo: PythonInfo,
  val isSystemWide: Boolean,
) : PythonSelectableInterpreter<P>() {
  override val homePath: P
    get() = sdkWrapper.homePath

  override fun toString(): String {
    return "ExistingSelectableInterpreter(sdk=${sdkWrapper.sdk}, pythonInfo=$pythonInfo, isSystemWide=$isSystemWide, homePath='$homePath')"
  }
}

/**
 * [isBase] is a system interpreter, see [isBasePython]
 */
class DetectedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val pythonInfo: PythonInfo,
  val isBase: Boolean,
  override val ui: PyToolUIInfo? = null,
) : PythonSelectableInterpreter<P>() {
  override fun toString(): String {
    return "DetectedSelectableInterpreter(homePath='$homePath', pythonInfo=$pythonInfo, isBase=$isBase, uiCustomization=$ui)"
  }
}

class ManuallyAddedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val pythonInfo: PythonInfo,
) : PythonSelectableInterpreter<P>() {
  override fun toString(): String {
    return "ManuallyAddedSelectableInterpreter(homePath='$homePath', pythonInfo=$pythonInfo)"
  }
}

class InstallableSelectableInterpreter<P : PathHolder>(
  override val pythonInfo: PythonInfo,
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
  val targetPanelExtension: ObservableMutableProperty<TargetPanelExtension?> = propertyGraph.property(null)
}

class MutableTargetState<P : PathHolder>(propertyGraph: PropertyGraph) : AddInterpreterState<P>(propertyGraph) {
  val baseInterpreter: ObservableMutableProperty<PythonSelectableInterpreter<P>?> = propertyGraph.property(null)
}


internal val <P : PathHolder> PythonAddInterpreterModel<P>.existingSdks: List<Sdk>
  get() = allInterpreters.value?.filterIsInstance<ExistingSelectableInterpreter<P>>()?.map { it.sdkWrapper.sdk } ?: emptyList()

internal suspend fun PythonAddInterpreterModel<*>.getBasePath(module: Module?): Path = withContext(Dispatchers.IO) {
  val pyProjectTomlBased = module?.let { PyProjectToml.findFile(it)?.toNioPathOrNull()?.parent }

  pyProjectTomlBased ?: module?.basePath?.let { Path.of(it) } ?: projectPathFlows.projectPathWithDefault.first()
}
