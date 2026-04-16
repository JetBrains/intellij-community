// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pytools.Version
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.add.v2.conda.CondaViewModel
import com.jetbrains.python.sdk.add.v2.hatch.HatchViewModel
import com.jetbrains.python.sdk.add.v2.pipenv.PipenvViewModel
import com.jetbrains.python.sdk.add.v2.poetry.PoetryViewModel
import com.jetbrains.python.sdk.add.v2.uv.UvViewModel
import com.jetbrains.python.sdk.add.v2.venv.VenvViewModel
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

interface PythonToolViewModel {
  fun initialize(scope: CoroutineScope)
}

abstract class PythonAddInterpreterModel<P : PathHolder>(
  val projectPathFlows: ProjectPathFlows,
  val fileSystem: FileSystem<P>,
) {
  internal val modificationCounter: AtomicProperty<Int> = AtomicProperty(0)

  internal val propertyGraph: PropertyGraph = PropertyGraph()
  internal val navigator: PythonNewEnvironmentDialogNavigator = PythonNewEnvironmentDialogNavigator()
  open val state: AddInterpreterState<P> = AddInterpreterState(propertyGraph)

  val condaViewModel: CondaViewModel<P> = CondaViewModel(fileSystem, propertyGraph, projectPathFlows)
  internal val uvViewModel: UvViewModel<P> = UvViewModel(fileSystem, propertyGraph, projectPathFlows)
  internal val pipenvViewModel: PipenvViewModel<P> = PipenvViewModel(fileSystem, propertyGraph)
  internal val poetryViewModel: PoetryViewModel<P> = PoetryViewModel(fileSystem, propertyGraph)
  internal val hatchViewModel: HatchViewModel<P> = HatchViewModel(fileSystem, propertyGraph, projectPathFlows)
  val venvViewModel: VenvViewModel<P> = VenvViewModel(fileSystem, propertyGraph, projectPathFlows)

  private val knownInterpreters: MutableStateFlow<List<PythonSelectableInterpreter<P>>?> = MutableStateFlow(null)
  private val detectedInterpretersUnfiltered: MutableStateFlow<List<DetectedSelectableInterpreter<P>>?> = MutableStateFlow(null)
  lateinit var detectedInterpreters: StateFlow<List<DetectedSelectableInterpreter<P>>?>
  internal val manuallyAddedInterpreters: MutableStateFlow<List<ManuallyAddedSelectableInterpreter<P>>> = MutableStateFlow(emptyList())
  private var installable: List<InstallableSelectableInterpreter<P>> = emptyList()
  internal lateinit var allInterpreters: StateFlow<List<PythonSelectableInterpreter<P>>?>
  lateinit var baseInterpreters: StateFlow<List<PythonSelectableInterpreter<P>>?>

  @TestOnly
  @ApiStatus.Internal
  fun addKnown(known: PythonSelectableInterpreter<P>) {
    knownInterpreters.value?.let { existing ->
      knownInterpreters.value = existing + known
    }
  }

  @TestOnly
  @ApiStatus.Internal
  fun addDetected(detected: DetectedSelectableInterpreter<P>) {
    detectedInterpretersUnfiltered.value?.let { existing ->
      detectedInterpretersUnfiltered.value = existing + detected
    }
  }

  // If the project is provided, sdks associated with it will be kept in the list of interpreters. If not, then they will be filtered out.
  open fun initialize(scope: CoroutineScope) {
    listOf(condaViewModel, uvViewModel, pipenvViewModel, poetryViewModel, hatchViewModel, venvViewModel).forEach { it.initialize(scope) }

    this.detectedInterpreters = combine(
      knownInterpreters,
      detectedInterpretersUnfiltered,
    ) { known, unfiltered ->
      val existingSdkPaths = known?.map { it.homePath }?.toSet() ?: return@combine null
      unfiltered?.filterNot { it.homePath in existingSdkPaths }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    merge(
      projectPathFlows.projectPathWithDefault,
      knownInterpreters,
      detectedInterpreters,
      manuallyAddedInterpreters,
      condaViewModel.condaEnvironmentsResult,
      hatchViewModel.availableEnvironments,
    ).map {
      modificationCounter.updateAndGet { it + 1 }
    }.launchIn(scope + Dispatchers.EDT)

    scope.launch(TraceContext(message("trace.context.loading.interpreter.list"), scope) + Dispatchers.EDT) {
      installable = fileSystem.getInstallableInterpreters()
      val projectPathPrefix = projectPathFlows.projectPathWithDefault.first()
      val existingSelectableInterpreters = fileSystem.getExistingSelectableInterpreters(projectPathPrefix)
      knownInterpreters.value = existingSelectableInterpreters
      val detectedSelectableInterpreters = withContext(Dispatchers.IO) { fileSystem.detectSelectableVenv(projectPathPrefix) }
      detectedInterpretersUnfiltered.value = detectedSelectableInterpreters
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

    this.baseInterpreters = combine( // base pythons are always system only
      detectedInterpretersUnfiltered.map { it?.sysPythonsOnly() },
      manuallyAddedInterpreters.sysPythonsOnly()
    ) { detected, manual ->
      val base = detected ?: return@combine null
      val existingLanguageLevels = base.map { it.pythonInfo.languageLevel }.toSet()
      val nonExistingInstallable = installable.filter { it.pythonInfo.languageLevel !in existingLanguageLevels }
      manual + base.sorted() + nonExistingInstallable
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = null)
  }


  internal fun addManuallyAddedInterpreter(interpreter: ManuallyAddedSelectableInterpreter<P>) {
    manuallyAddedInterpreters.value += interpreter
    state.selectedInterpreter.set(interpreter)
  }

  internal suspend fun addManuallyAddedPythonNotNecessarilySystem(homePath: P) =
    addManuallyAddedInterpreter(homePath, requireSystemPython = false)

  internal suspend fun addManuallyAddedSystemPython(homePath: P) = addManuallyAddedInterpreter(homePath, requireSystemPython = true)
  private suspend fun addManuallyAddedInterpreter(
    homePath: P,
    requireSystemPython: Boolean,
  ): PyResult<ManuallyAddedSelectableInterpreter<P>> {
    val python = homePath.let { fileSystem.getSystemPythonFromSelection(it, requireSystemPython) }.getOr { return it }

    val interpreter = ManuallyAddedSelectableInterpreter(homePath, python.pythonInfo, isBase = python.isBase).also {
      this@PythonAddInterpreterModel.addManuallyAddedInterpreter(it)
    }
    return PyResult.success(interpreter)
  }


  @RequiresEdt
  internal fun addInstalledInterpreter(homePath: P, pythonInfo: PythonInfo): DetectedSelectableInterpreter<P> {
    val installedInterpreter = DetectedSelectableInterpreter(homePath, pythonInfo, true)
    detectedInterpretersUnfiltered.value = (detectedInterpretersUnfiltered.value ?: emptyList()) + installedInterpreter
    return installedInterpreter
  }
}

abstract class PythonMutableTargetAddInterpreterModel<P : PathHolder>(projectPathFlows: ProjectPathFlows, fileSystem: FileSystem<P>) :
  PythonAddInterpreterModel<P>(projectPathFlows, fileSystem) {
  override val state: MutableTargetState<P> = MutableTargetState(propertyGraph)
}

class PythonLocalAddInterpreterModel<P : PathHolder>(projectPathFlows: ProjectPathFlows, fileSystem: FileSystem<P>) :
  PythonMutableTargetAddInterpreterModel<P>(projectPathFlows, fileSystem) {
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
  val pyProjectTomlBased = module?.let { PyProjectToml.findPyProjectTomlFile(it)?.virtualFile?.toNioPathOrNull()?.parent }

  pyProjectTomlBased ?: module?.baseDir?.path?.let { Path.of(it) } ?: projectPathFlows.projectPathWithDefault.first()
}

private fun <T : MaybeSystemPython> Flow<Iterable<T>>.sysPythonsOnly(): Flow<List<T>> = map { it.sysPythonsOnly() }
private fun <T : MaybeSystemPython> Iterable<T>.sysPythonsOnly(): List<T> = filter { it.isBase }
