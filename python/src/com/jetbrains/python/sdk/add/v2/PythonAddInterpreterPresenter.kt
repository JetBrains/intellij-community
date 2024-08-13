// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.util.text.nullize
import com.jetbrains.python.sdk.add.ProjectLocationContext
import com.jetbrains.python.sdk.add.ProjectLocationContexts
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext


/**
 * Note. This class could be made a view-model in Model-View-ViewModel pattern. This would completely decouple its logic from the view. To
 * achieve that a separate management of its lifecycle is required using its own [CoroutineScope].
 *
 * @param state is the model for this presented in Model-View-Presenter pattern
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class PythonAddInterpreterPresenter(val state: PythonAddInterpreterState, val uiContext: CoroutineContext) {

  lateinit var controller: PythonAddInterpreterModel

  val scope: CoroutineScope
    get() = state.scope

  private val _allExistingSdksFlow = MutableStateFlow(state.allExistingSdks.get())

  private val projectLocationContexts = ProjectLocationContexts()

  private val _projectWithContextFlow: MutableStateFlow<ProjectPathWithContext> =
    MutableStateFlow(associateWithContext(state.projectPath.get()))

  private val _projectLocationContext: StateFlow<ProjectLocationContext> =
    _projectWithContextFlow.mapStateIn(scope + uiContext, SharingStarted.Lazily) { it.context }

  private val _detectingCondaExecutable = MutableStateFlow(value = false)

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
  val projectLocationContext: ProjectLocationContext
    get() = _projectWithContextFlow.value.context

  val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = projectLocationContext.targetEnvironmentConfiguration
  var baseConda: PyCondaEnv? = null

  private val _currentCondaExecutableFlow = MutableStateFlow<Path?>(value = null)

  init {
    state.projectPath.afterChange { projectPath ->
      val context = projectLocationContexts.getProjectLocationContextFor(projectPath)
      _projectWithContextFlow.value = ProjectPathWithContext(projectPath, context)
    }

    state.allExistingSdks.afterChange { _allExistingSdksFlow.tryEmit(it) }
    state.installableSdks.afterChange { _sdksToInstall.tryEmit(it) }
    state.condaExecutable.afterChange {
      if (!it.startsWith("<")) _currentCondaExecutableFlow.tryEmit(tryConvertToPath(it)) // skip possible <unknown_executable>
    }

    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      _projectLocationContext
        .mapLatest { context ->
          withContext(uiContext) {
            reloadConda(context)
          }
        }
        .catch { LOG.error(it) }
        .collect()
    }
  }

  private suspend fun reloadConda(context: ProjectLocationContext) {
    _detectingCondaExecutable.value = true
    val executor = context.targetEnvironmentConfiguration.toExecutor()
    val suggestedCondaPath = runCatching { suggestCondaPath(targetCommandExecutor = executor) }.getOrLogException(LOG)
    val suggestedCondaLocalPath = suggestedCondaPath?.toLocalPathOn(context.targetEnvironmentConfiguration) ?: UNKNOWN_EXECUTABLE
    state.condaExecutable.set(suggestedCondaLocalPath.toString())
    val environments = suggestedCondaPath?.let { PyCondaEnv.getEnvs(executor, suggestedCondaPath).getOrLogException(LOG) }
    baseConda = environments?.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }
    _detectingCondaExecutable.value = false
  }

  private fun tryConvertToPath(path: String): Path? {
    return path.nullize(nullizeSpaces = true)?.let {
      try {
        Path.of(it)
      }
      catch (e: InvalidPathException) {
        null
      }
    }
  }

  data class ProjectPathWithContext(val projectPath: String, val context: ProjectLocationContext)

  private fun associateWithContext(path: String): ProjectPathWithContext {
    return ProjectPathWithContext(projectPath = path, projectLocationContexts.getProjectLocationContextFor(projectPath = path))
  }

  companion object {
    val LOG = logger<PythonAddInterpreterPresenter>()
  }
}