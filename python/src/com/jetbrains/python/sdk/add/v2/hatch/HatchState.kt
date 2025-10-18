// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.application.UI
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.hatch.HatchConfiguration.getOrDetectHatchExecutablePath
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.jetbrains.python.Result
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.add.v2.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.path.isDirectory

class HatchState<P : PathHolder>(
  val fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
  val projectPathFlows: ProjectPathFlows,
) : ToolState {
  val selectedHatchEnv: ObservableMutableProperty<HatchVirtualEnvironment?> = propertyGraph.property(null)
  val hatchEnvironmentsResult: MutableStateFlow<PyResult<List<HatchVirtualEnvironment>>?> = MutableStateFlow(null)
  val hatchExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "hatch",
    backProperty = hatchExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      when (fileSystem) {
        is FileSystem.Eel -> getOrDetectHatchExecutablePath(fileSystem.eelApi).getOrNull()?.let { PathHolder.Eel(it) } as P?
        else -> null
      }
    }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)

    hatchExecutable.afterChange { hatchExecutable ->
      if (hatchExecutable?.validationResult?.successOrNull == null) {
        hatchEnvironmentsResult.value = null
        return@afterChange
      }

      val binaryToExec = hatchExecutable.pathHolder?.let { fileSystem.getBinaryToExec(it) }
                         ?: return@afterChange
      scope.launch(Dispatchers.UI) {
        hatchEnvironmentsResult.value = detectHatchEnvironments(binaryToExec)
      }
    }
  }

  private suspend fun detectHatchEnvironments(hatchExecutable: BinaryToExec): PyResult<List<HatchVirtualEnvironment>> = withContext(Dispatchers.IO) {
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
}
