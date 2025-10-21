// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.openapi.application.UI
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CondaViewModel<P : PathHolder>(
  val fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
) : PythonToolViewModel {
  val condaExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val condaEnvironmentsResult: MutableStateFlow<PyResult<List<PyCondaEnv>>?> = MutableStateFlow(null)
  val condaEnvironmentsLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val selectedCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)
  val baseCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)
  val newCondaEnvName: ObservableMutableProperty<String> = propertyGraph.property("")
  lateinit var scope: CoroutineScope

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "conda",
    backProperty = condaExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = { fileSystem.which("conda") }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)
    this.scope = scope

    condaExecutable.afterChange { condaExecutable ->
      condaEnvironmentsResult.value = null
      selectedCondaEnv.set(null)
      baseCondaEnv.set(null)

      if (condaExecutable?.validationResult?.successOrNull != null) {
        detectCondaEnvironments()
      }
    }
  }

  fun detectCondaEnvironments() {
    condaEnvironmentsLoading.value = true
    scope.launch(Dispatchers.UI) {
      condaEnvironmentsResult.value = updateCondaEnvironments()
    }.invokeOnCompletion {
      condaEnvironmentsLoading.value = false
    }
  }

  /**
   * Returns error or `null` if no error
   */
  private suspend fun updateCondaEnvironments(): PyResult<List<PyCondaEnv>> = withContext(Dispatchers.IO) {
    val executable = condaExecutable.get()
    if (executable == null) return@withContext PyResult.localizedError(message("python.sdk.conda.no.exec"))
    executable.validationResult.getOr { return@withContext it }

    val binaryToExec = executable.pathHolder?.let { fileSystem.getBinaryToExec(it) }!!
    val environments = PyCondaEnv.getEnvs(binaryToExec).getOr { return@withContext it }
    val baseConda = environments.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }

    withContext(Dispatchers.UI) {
      baseCondaEnv.set(baseConda)
      selectedCondaEnv.set(environments.firstOrNull())
    }
    return@withContext PyResult.success(environments)
  }
}