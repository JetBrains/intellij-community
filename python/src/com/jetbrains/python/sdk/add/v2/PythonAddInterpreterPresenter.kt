// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.flow.mapStateIn
import com.intellij.util.text.nullize
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.add.LocalContext
import com.jetbrains.python.sdk.add.ProjectLocationContext
import com.jetbrains.python.sdk.add.ProjectLocationContexts
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.add.target.createDetectedSdk
import com.jetbrains.python.sdk.detectSystemWideSdksSuspended
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.prepareSdkList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

private val emptyContext: UserDataHolder by lazy { UserDataHolderBase() }

internal fun PythonAddInterpreterPresenter.tryGetVirtualFile(pathOnTarget: FullPathOnTarget): VirtualFile? {
  val mapper = targetEnvironmentConfiguration?.let { PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(it) }
  return if (mapper != null) mapper.getVfsFromTargetPath(pathOnTarget) else LocalFileSystem.getInstance().findFileByPath(pathOnTarget)
}

internal fun PythonAddInterpreterPresenter.getPathOnTarget(path: Path): String = path.convertToPathOnTarget(targetEnvironmentConfiguration)

/**
 * Note. This class could be made a view-model in Model-View-ViewModel pattern. This would completely decouple its logic from the view. To
 * achieve that a separate management of its lifecycle is required using its own [CoroutineScope].
 *
 * @param state is the model for this presented in Model-View-Presenter pattern
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PythonAddInterpreterPresenter(val state: PythonAddInterpreterState, val uiContext: CoroutineContext) {
  val scope: CoroutineScope
    get() = state.scope

  private val _allExistingSdksFlow = MutableStateFlow(state.allExistingSdks.get())

  private val projectLocationContexts = ProjectLocationContexts()

  private val _projectWithContextFlow: MutableStateFlow<ProjectPathWithContext> =
    MutableStateFlow(state.projectPath.get().associateWithContext())

  private val _projectLocationContext: StateFlow<ProjectLocationContext> =
    _projectWithContextFlow.mapStateIn(scope + uiContext, SharingStarted.Lazily) { it.context }

  private val _detectingSdks = MutableStateFlow(value = false)
  val detectingSdks: StateFlow<Boolean> = _detectingSdks.asStateFlow()

  private val _detectingCondaExecutable = MutableStateFlow(value = false)
  val detectingCondaExecutable: StateFlow<Boolean> = _detectingCondaExecutable.asStateFlow()

  val navigator = PythonNewEnvironmentDialogNavigator()

  /**
   * Prefer using this flow over [PythonAddInterpreterState.projectPath] of the [state] when reacting to the changes of the new project
   * location (either with background computations or UI).
   *
   * [PythonAddInterpreterState.projectPath] of the [state] is *practically* safe to use at the final stage of creating the interpreter.
   *
   * @see PythonAddInterpreterState.projectPath
   */
  val projectWithContextFlow: StateFlow<ProjectPathWithContext> = _projectWithContextFlow.asStateFlow()
  val projectLocationContext: ProjectLocationContext
    get() = _projectWithContextFlow.value.context

  private val detectedSdksFlow: StateFlow<Pair<ProjectLocationContext, List<Sdk>>> =
    _projectLocationContext
      .mapLatest { context ->
        _detectingSdks.value = true
        val sdks = runCatching {
          detectSystemWideSdksSuspended(module = null, context.targetEnvironmentConfiguration, emptyContext)
        }.getOrLogException(LOG) ?: emptyList()
        _detectingSdks.value = false
        context to sdks
      }
      .logException(LOG)
      .stateIn(scope + uiContext, started = SharingStarted.Lazily, LocalContext to emptyList())

  private val manuallyAddedSdksFlow = MutableStateFlow<List<Sdk>>(emptyList())

  val allSdksFlow: StateFlow<List<Sdk>> =
    combine(_allExistingSdksFlow, manuallyAddedSdksFlow) { existingSdks, manuallyAddedSdks ->
      manuallyAddedSdks + existingSdks
    }
      .stateIn(scope + uiContext, started = SharingStarted.Lazily, initialValue = emptyList())

  val basePythonSdksFlow: StateFlow<List<Sdk>> =
    combine(_allExistingSdksFlow, manuallyAddedSdksFlow, detectedSdksFlow) { existingSdks, manuallyAddedSdks, (context, detectedSdks) ->
      val sdkList = manuallyAddedSdks + withContext(Dispatchers.IO) {
        prepareSdkList(detectedSdks, existingSdks, context.targetEnvironmentConfiguration)
      }
      state.basePythonSdks.set(sdkList)
      sdkList
    }
      .logException(LOG)
      .stateIn(scope + uiContext, started = SharingStarted.Lazily, initialValue = emptyList())

  val basePythonHomePath = state.basePythonVersion.transform(
    map = { sdk -> sdk?.homePath ?: "" },
    backwardMap = { path -> state.basePythonSdks.get().find { it.homePath == path }!! }
  )
  val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = projectLocationContext.targetEnvironmentConfiguration
  var baseConda: PyCondaEnv? = null

  private val _currentCondaExecutableFlow = MutableStateFlow<Path?>(value = null)
  val currentCondaExecutableFlow: StateFlow<Path?> = _currentCondaExecutableFlow.asStateFlow()

  init {
    state.projectPath.afterChange { projectPath ->
      val context = projectLocationContexts.getProjectLocationContextFor(projectPath)
      _projectWithContextFlow.value = ProjectPathWithContext(projectPath, context)
    }

    state.allExistingSdks.afterChange { _allExistingSdksFlow.tryEmit(it) }
    state.condaExecutable.afterChange {
      if (!it.startsWith("<")) _currentCondaExecutableFlow.tryEmit(it.tryConvertToPath()) // skip possible <unknown_executable>
    }

    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      _projectLocationContext
        .mapLatest { context ->
          withContext(uiContext) {
            _detectingCondaExecutable.value = true
            val executor = context.targetEnvironmentConfiguration.toExecutor()
            val suggestedCondaPath = runCatching { suggestCondaPath(targetCommandExecutor = executor) }.getOrLogException(LOG)
            val suggestedCondaLocalPath = suggestedCondaPath?.toLocalPathOn(context.targetEnvironmentConfiguration)
            state.condaExecutable.set(suggestedCondaLocalPath?.toString().orEmpty())
            val environments = suggestedCondaPath?.let { PyCondaEnv.getEnvs(executor, suggestedCondaPath).getOrLogException(LOG) }
            baseConda = environments?.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }
            _detectingCondaExecutable.value = false
          }
        }
        .logException(LOG)
        .collect()
    }
  }

  private fun String.tryConvertToPath(): Path? =
    nullize(nullizeSpaces = true)?.let {
      try {
        Path.of(it)
      }
      catch (e: InvalidPathException) {
        null
      }
    }

  /**
   * Adds SDK specified by its path if it is not present in the list and selects it.
   */
  @RequiresEdt
  fun addAndSelectBaseSdk(path: String) {
    val sdkAdded = createDetectedSdk(path, targetEnvironmentConfiguration)
    // TODO probably remove duplicates
    manuallyAddedSdksFlow.value += sdkAdded
  }

  private fun String.associateWithContext(): ProjectPathWithContext =
    ProjectPathWithContext(projectPath = this, projectLocationContexts.getProjectLocationContextFor(projectPath = this))

  data class ProjectPathWithContext(val projectPath: String, val context: ProjectLocationContext)

  companion object {
    private val LOG = logger<PythonAddInterpreterPresenter>()
  }
}