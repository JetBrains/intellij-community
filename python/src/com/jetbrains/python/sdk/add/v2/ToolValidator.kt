// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.UI
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.jetbrains.python.PyBundle
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.*


class ToolValidator<P : PathHolder>(
  val fileSystem: FileSystem<P>,
  val toolVersionPrefix: String,
  override val backProperty: ObservableMutableProperty<ValidatedPath.Executable<P>?>,
  propertyGraph: PropertyGraph,
  val defaultPathSupplier: suspend () -> P?,
  val pathValidator: suspend (P) -> PyResult<Version> = { fileSystem.getBinaryToExec(it).getToolVersion(toolVersionPrefix) },
) : PathValidator<Version, P, ValidatedPath.Executable<P>> {
  override val isDirtyValue: ObservableMutableProperty<Boolean> = propertyGraph.property(true)
  override val isValidationInProgress: Boolean
    get() = validationJob.isActive

  lateinit var scope: CoroutineScope
  private lateinit var validationJob: Deferred<Unit>

  fun initialize(scope: CoroutineScope) {
    this.scope = scope
    this.validationJob = autodetectExecutableJob()
  }

  override fun validate(input: String) {
    scope.launch {
      if (input.isEmpty()) {
        autodetectExecutable()
        return@launch
      }
      validationJob.cancelAndJoin()
      validationJob = scope.async {
        withContext(Dispatchers.UI) { isDirtyValue.set(true) }

        val exec = withContext(Dispatchers.IO) {
          val path = fileSystem.parsePath(input).getOr { error ->
            return@withContext ValidatedPath.Executable<P>(null, error)
          }
          val validationResult = pathValidator(path)
          ValidatedPath.Executable(path, validationResult)
        }

        withContext(Dispatchers.UI) { backProperty.set(exec) }
      }
      validationJob.invokeOnCompletion {
        isDirtyValue.set(false)
      }
    }
  }


  suspend fun autodetectExecutable() {
    validationJob.cancelAndJoin()
    validationJob = autodetectExecutableJob()
  }

  private fun autodetectExecutableJob(): Deferred<Unit> {
    return scope.async(TraceContext(PyBundle.message("tracecontext.detecting.executable", toolVersionPrefix), scope)) {
      withContext(Dispatchers.UI) { isDirtyValue.set(true) }
      val validatedPath = fileSystem.autodetectWithVersionProbe(toolVersionPrefix, defaultPathSupplier)
      withContext(Dispatchers.UI) { backProperty.set(validatedPath) }
    }.apply {
      invokeOnCompletion {
        isDirtyValue.set(false)
      }
    }
  }

  companion object {

    suspend fun <P : PathHolder> FileSystem<P>.autodetectWithVersionProbe(
      toolVersionPrefix: String,
      toolPathSupplier: suspend () -> P?,
    ): ValidatedPath.Executable<P> = withContext(Dispatchers.IO) {
      val path = toolPathSupplier.invoke()
      val validatedPath = path?.validateToolExecutableByVersionProbe(this@autodetectWithVersionProbe, toolVersionPrefix)
                          ?: ValidatedPath.Executable(
                            pathHolder = path,
                            validationResult = PyResult.localizedError(PyBundle.message("python.sdk.executable.is.not.detected"))
                          )
      validatedPath
    }

    private suspend fun <P : PathHolder> P.validateToolExecutableByVersionProbe(fileSystem: FileSystem<P>, toolVersionPrefix: String): ValidatedPath.Executable<P> {
      val binaryToExec = fileSystem.getBinaryToExec(this)
      val validationResult = binaryToExec.getToolVersion(toolVersionPrefix)
      return ValidatedPath.Executable(
        pathHolder = this,
        validationResult = validationResult
      )
    }
  }
}