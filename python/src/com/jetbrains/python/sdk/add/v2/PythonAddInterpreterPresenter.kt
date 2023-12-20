// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.nullize
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.add.LocalContext
import com.jetbrains.python.sdk.add.ProjectLocationContext
import com.jetbrains.python.sdk.add.ProjectLocationContexts
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.add.target.createDetectedSdk
import com.jetbrains.python.sdk.configuration.createVirtualEnvSynchronously
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

internal fun PythonAddInterpreterPresenter.getPathOnTarget(path: Path): @NlsSafe String =
  path.convertToPathOnTarget(targetEnvironmentConfiguration)


internal fun PythonAddInterpreterPresenter.setupVirtualenv(venvPath: Path, projectPath: String, baseSdk: Sdk): Sdk? {
  val venvPathOnTarget = getPathOnTarget(venvPath)
  val savedSdk = installBaseSdk(baseSdk, state.allSdks.get()) ?: return null
  val sdk = createVirtualEnvSynchronously(savedSdk, state.allSdks.get(), venvPathOnTarget,
                                          projectPath, null, null) ?: error("Failed to create SDK")
  SdkConfigurationUtil.addSdk(sdk)
  return sdk
}

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

  private val _sdksToInstall: MutableStateFlow<List<Sdk>> = MutableStateFlow(emptyList())

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

  private val manuallyAddedSdksFlow = MutableStateFlow<List<PyDetectedSdk>>(emptyList())
  private val manuallyAddedBaseSdksFlow = MutableStateFlow<List<PyDetectedSdk>>(emptyList())

  val allSdksFlow: StateFlow<List<Sdk>> =
    combine(_allExistingSdksFlow, manuallyAddedSdksFlow, manuallyAddedBaseSdksFlow) { existingSdks, addedSdks, addedBaseSdks ->
      val filteredAddedSdks = addedSdks
        .filterNot { existingSdks.hasSamePythonInterpreter(it) }
      val filteredAddedBaseSdks = addedBaseSdks
        .filterNot { existingSdks.hasSamePythonInterpreter(it) }
        .filterNot { filteredAddedSdks.hasSamePythonInterpreter(it) }
      val allSdks = filteredAddedSdks + filteredAddedBaseSdks + existingSdks
      state.allSdks.set(allSdks)
      allSdks
    }
      .logException(LOG)
      .stateIn(scope + uiContext, started = SharingStarted.Lazily, initialValue = emptyList())

  val basePythonSdksFlow: StateFlow<List<Sdk>> =
    combine(_allExistingSdksFlow, manuallyAddedBaseSdksFlow, detectedSdksFlow, _sdksToInstall) { existingSdks, addedBaseSdks, (context, detectedSdks), sdkListToInstall ->
      val sdkList = addedBaseSdks + withContext(Dispatchers.IO) {
        val baseSdks = prepareSdkList(detectedSdks, existingSdks, context.targetEnvironmentConfiguration)
        baseSdks + filterInstallableSdks(sdkListToInstall, baseSdks)
      }
      state.basePythonSdks.set(sdkList)
      sdkList
    }
      .logException(LOG)
      .stateIn(scope + uiContext, started = SharingStarted.Lazily, initialValue = emptyList())

  private fun filterInstallableSdks(sdkListToInstall: List<Sdk>, sdkList: List<Sdk>): List<Sdk> {
    val languageLevels = sdkList.map { PySdkUtil.getLanguageLevelForSdk(it) }
    return sdkListToInstall
      .map { LanguageLevel.fromPythonVersion(it.versionString) to it }
      .filter { it.first !in languageLevels }
      .sortedByDescending { it.first }
      .map { it.second }
  }

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
    state.installableSdks.afterChange { _sdksToInstall.tryEmit(it) }
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
   * Adds SDK specified by its path if it is not present in the list.
   */
  @RequiresEdt
  fun addPythonInterpreter(targetPath: String) {
    manuallyAddedSdksFlow.addDetectedSdk(targetPath, targetEnvironmentConfiguration)
  }

  /**
   * Adds base SDK specified by its path if it is not present in the list.
   */
  @RequiresEdt
  fun addBasePythonInterpreter(targetPath: String) {
    manuallyAddedBaseSdksFlow.addDetectedSdk(targetPath, targetEnvironmentConfiguration)
  }

  private fun String.associateWithContext(): ProjectPathWithContext =
    ProjectPathWithContext(projectPath = this, projectLocationContexts.getProjectLocationContextFor(projectPath = this))

  data class ProjectPathWithContext(val projectPath: String, val context: ProjectLocationContext)

  companion object {
    private val LOG = logger<PythonAddInterpreterPresenter>()

    private fun MutableStateFlow<List<PyDetectedSdk>>.addDetectedSdk(targetPath: String,
                                                                     targetEnvironmentConfiguration: TargetEnvironmentConfiguration?) {
      value = value.addDetectedSdk(targetPath, targetEnvironmentConfiguration)
    }

    private fun List<PyDetectedSdk>.addDetectedSdk(targetPath: String,
                                                   targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): List<PyDetectedSdk> =
      if (!containsSdkWithHomePath(targetPath)) this + createDetectedSdk(targetPath, targetEnvironmentConfiguration) else this

    private fun Collection<Sdk>.hasSamePythonInterpreter(sdk: Sdk) = any { it.homePath == sdk.homePath }

    private fun Collection<Sdk>.containsSdkWithHomePath(targetPath: String) = any { it.homePath == targetPath }
  }
}