// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.python.community.services.shared.VanillaPythonWithLanguageLevel
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
internal abstract class CustomExistingEnvironmentSelector(
  private val name: String,
  model: PythonMutableTargetAddInterpreterModel,
  private val module: Module?,
) : PythonExistingEnvironmentConfigurator(model) {

  private lateinit var comboBox: PythonInterpreterComboBox
  private val existingEnvironments: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  protected val selectedEnv: ObservableMutableProperty<PythonSelectableInterpreter?> = propertyGraph.property(null)

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      executableSelector(
        executable = executable,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", name),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", name),
      ).component

      val nameTitle = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
      comboBox = pythonInterpreterComboBox(
        title = message("sdk.create.custom.existing.env.title", nameTitle),
        selectedSdkProperty = selectedEnv,
        model = model,
        validationRequestor = validationRequestor,
        onPathSelected = { path -> addEnvByPath(path) }
      ) {
        visibleIf(executable.notEqualsTo(""))
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

    comboBox.initialize(
      scope = scope,
      flow = existingEnvironments.map { existing ->
        val withUniquePath = existing.distinctBy { interpreter ->  interpreter.homePath }
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

  private fun addEnvByPath(python: VanillaPythonWithLanguageLevel): PythonSelectableInterpreter {
    val interpreter = ManuallyAddedSelectableInterpreter(python)
    existingEnvironments.value += interpreter
    return interpreter
  }

  internal abstract val executable: ObservableMutableProperty<String>
  internal abstract val interpreterType: InterpreterType
  internal abstract suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter>
}