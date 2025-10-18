// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.*


@Internal
internal abstract class CustomExistingEnvironmentSelector<P : PathHolder>(
  private val name: String,
  model: PythonMutableTargetAddInterpreterModel<P>,
  private val module: Module?,
) : PythonExistingEnvironmentConfigurator<P>(model) {

  private lateinit var comboBox: PythonInterpreterComboBox<P>
  private lateinit var executablePath: ValidatedPathField<Version, P, ValidatedPath.Executable<P>>

  private val existingEnvironments: MutableStateFlow<List<PythonSelectableInterpreter<P>>?> = MutableStateFlow(null)
  protected val selectedEnv: ObservableMutableProperty<PythonSelectableInterpreter<P>?> = propertyGraph.property(null)

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      executablePath = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = toolState,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", name),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", name),
      )

      val nameTitle = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
      comboBox = pythonInterpreterComboBox(
        model.fileSystem,
        title = message("sdk.create.custom.existing.env.title", nameTitle),
        selectedSdkProperty = selectedEnv,
        validationRequestor = validationRequestor,
        onPathSelected = model::addManuallyAddedInterpreter,
      ) {
        visibleIf(toolState.backProperty.transform { it?.validationResult?.successOrNull != null })
      }
    }
  }

  override fun onShown(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
      val basePath = model.getBasePath(module)
      val environments = detectEnvironments(basePath)

      withContext(Dispatchers.EDT) {
        existingEnvironments.value = environments
      }
    }

    executablePath.initialize(scope)
    comboBox.initialize(
      scope = scope,
      flow = existingEnvironments.map { existing ->
        existing ?: return@map null
        val withUniquePath = existing.distinctBy { interpreter -> interpreter.homePath }
        sortForExistingEnvironment(withUniquePath, module)
      }
    )
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    return InterpreterStatisticsInfo(
      type = interpreterType,
      target = target.toStatisticsField(),
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = true,
      isWSLContext = false,
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }

  //private fun addEnvByPath(python: VanillaPythonWithLanguageLevel): PythonSelectableInterpreter {
  //  val interpreter = ManuallyAddedSelectableInterpreter(python)
  //  existingEnvironments.value = (existingEnvironments.value ?: emptyList()) + interpreter
  //  return interpreter
  //}

  internal abstract val toolState: PathValidator<Version, P, ValidatedPath.Executable<P>>
  internal abstract val interpreterType: InterpreterType
  internal abstract suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter<P>>
}