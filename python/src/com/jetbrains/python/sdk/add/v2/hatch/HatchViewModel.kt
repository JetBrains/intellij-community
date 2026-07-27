// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.hatch.HATCH_TOML
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.HatchConfiguration.getOrDetectHatchExecutablePath
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonToolViewModel
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal class HatchViewModel<P : PathHolder>(
  val fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
  val projectPathFlows: ProjectPathFlows,
) : PythonToolViewModel {
  val selectedEnvFromAvailable: ObservableMutableProperty<HatchVirtualEnvironment<P>?> = propertyGraph.property(null)

  /**
   * there is a second field for selected existing env because the lookup values are different and the UI component doesn't reflect changes
   * if the selected env is not in the list.
   * (selection of non-existing env on the create new view and switching to the select existing view makes the UI inconsistent otherwise)
   */
  val selectedEnvFromExisting: ObservableMutableProperty<HatchVirtualEnvironment<P>?> = propertyGraph.property(null)
  val availableEnvironments: MutableStateFlow<PyResult<List<HatchVirtualEnvironment<P>>>?> = MutableStateFlow(null)
  val hatchExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "hatch",
    backProperty = hatchExecutable,
    propertyGraph = propertyGraph,
    toolCommandSpec = HatchConfiguration.toolCommandSpec,
    defaultPathSupplier = { getOrDetectHatchExecutablePath(fileSystem).successOrNull }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)

    hatchExecutable.afterChange { hatchExecutable ->
      if (hatchExecutable?.validationResult?.successOrNull == null) {
        availableEnvironments.value = PyResult.success(emptyList())
        return@afterChange
      }

      val hatchExecutablePath = hatchExecutable.pathHolder ?: return@afterChange
      scope.launch(Dispatchers.EDT) {
        availableEnvironments.value = detectHatchEnvironments(hatchExecutablePath)
      }
    }
  }

  private suspend fun detectHatchEnvironments(hatchExecutablePath: P): PyResult<List<HatchVirtualEnvironment<P>>> =
    withContext(Dispatchers.IO) {
      val projectPath = projectPathFlows.projectPathWithDefault.first()
      val hatchWorkingDirectory = generateSequence(projectPath) { it.parent }.firstOrNull { it.isDirectory() }
      val uploadBeforeExecution = hatchWorkingDirectory?.createHatchMetadataUploadConfig(hatchExecutablePath)
      val hatchService = hatchWorkingDirectory.getHatchService(
        fileSystem = fileSystem,
        hatchExecutablePath = hatchExecutablePath,
        uploadBeforeExecution = uploadBeforeExecution,
      ).getOr { return@withContext it }

      val hatchEnvironments = hatchService.findVirtualEnvironments().getOr { return@withContext it }
      val availableEnvironments = when {
        hatchWorkingDirectory == projectPath -> hatchEnvironments
        else -> HatchVirtualEnvironment.availableEnvironmentsForNewProject()
      }
      success(availableEnvironments)
    }

  private fun Path.createHatchMetadataUploadConfig(hatchExecutablePath: P): UploadConfig? =
    when (hatchExecutablePath) {
      is PathHolder.Eel -> null
      is PathHolder.Target -> UploadConfig(
        relativePaths = listOf(PY_PROJECT_TOML, HATCH_TOML).filter { resolve(it).isRegularFile() }
      )
    }
}
