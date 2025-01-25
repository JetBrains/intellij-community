// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.util.ErrorSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.*


@Internal
internal abstract class CustomExistingEnvironmentSelector(private val name: String, model: PythonMutableTargetAddInterpreterModel, protected val moduleOrProject: ModuleOrProject) : PythonExistingEnvironmentConfigurator(model) {
  private lateinit var comboBox: PythonInterpreterComboBox
  protected val existingEnvironments: MutableStateFlow<List<PythonSelectableInterpreter>> = MutableStateFlow(emptyList())
  protected val selectedEnv: ObservableMutableProperty<PythonSelectableInterpreter?> = propertyGraph.property(null)

  init {
    model.scope.launch {
      val modulePath = when (moduleOrProject) {
        is ModuleOrProject.ProjectOnly -> moduleOrProject.project.basePath?.let { Path.of(it) }
        is ModuleOrProject.ModuleAndProject -> findModulePath(moduleOrProject.module)
      }

      if (modulePath != null) {
        detectEnvironments(modulePath)
      }
    }
  }

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor, errorSink: ErrorSink) {
    with(panel) {
      executableSelector(
        executable,
        validationRequestor,
        message("sdk.create.custom.venv.executable.path", name),
        message("sdk.create.custom.venv.missing.text", name),
      ).component

      addInterpretersComboBox(panel)
    }
  }

  protected open fun addInterpretersComboBox(panel: Panel) {
    with(panel) {
      row(message("sdk.create.custom.existing.env.title", name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })) {
        comboBox = pythonInterpreterComboBox(selectedEnv, model, { path -> addEnvByPath(path) }, model.interpreterLoading)
          .align(Align.FILL)
          .component
      }.visibleIf(executable.notEqualsTo(""))
    }
  }

  override fun onShown() {
    comboBox.setItems(existingEnvironments)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val statisticsTarget = target.toStatisticsField()

    return InterpreterStatisticsInfo(interpreterType,
                                     statisticsTarget,
                                     false,
                                     false,
                                     true,
                                     false,
                                     InterpreterCreationMode.CUSTOM)
  }

  private fun addEnvByPath(path: String) {
    val languageLevel = PySdkUtil.getLanguageLevelForSdk(PythonSdkUtil.findSdkByKey(path))
    val interpreter = ManuallyAddedSelectableInterpreter(path, languageLevel)
    existingEnvironments.value += interpreter
  }

  internal abstract val executable: ObservableMutableProperty<String>
  internal abstract val interpreterType: InterpreterType
  internal abstract suspend fun detectEnvironments(modulePath: Path)
  internal abstract suspend fun findModulePath(module: Module): Path?
}