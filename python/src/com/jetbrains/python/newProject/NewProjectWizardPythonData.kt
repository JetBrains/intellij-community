// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.onFailure
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PySdkCreator
import com.jetbrains.python.sdk.add.v2.PythonSdkPanelBuilderAndSdkCreator
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.io.files.SystemPathSeparator
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent

/**
 * Data for sharing among the steps of the new Python project wizard.
 */
interface NewProjectWizardPythonData : NewProjectWizardBaseData {

  /**
   * A property for tracking changes in [pythonSdk].
   */
  val pythonSdkProperty: GraphProperty<Sdk?>

  /**
   * The Python SDK for the new Python project or module.
   *
   * During [NewProjectWizardStep.setupUI] it reflects the selected Python SDK (it may be `null` for a new environment or if there is no
   * Python installed on the machine). After [PythonSdkStep] gets or creates the actual SDK for the new project in its
   * [NewProjectWizardStep.setupProject], the attribute contains the actual SDK.
   */
  var pythonSdk: Sdk?

  /**
   * The Python module after it has been created during [NewProjectWizardStep.setupProject].
   */
  val module: Module?
}

private fun <T> GraphProperty<T>.toFlow(): Flow<T> {
  val flow = MutableStateFlow(get())
  this.afterChange {
    flow.value = it
  }
  return flow
}

/**
 * A new Python project wizard step that allows you to either create a new Python environment or select an existing Python interpreter.
 *
 * It works for both PyCharm (where the *.iml file resides in .idea/ directory and the SDK is set for the project) and other
 * IntelliJ-based IDEs (where the *.iml file resides in the module directory and the SDK is set for the module).
 */
class NewPythonProjectStep(parent: NewProjectWizardStep, val createPythonModuleStructure: Boolean)
  : AbstractNewProjectWizardStep(parent),
    NewProjectWizardBaseData by parent.baseData!!,
    NewProjectWizardPythonData {
  constructor(parent: NewProjectWizardStep) : this(parent, false) // a separated constructor was made for compatibility with existing plugins

  override val pythonSdkProperty: GraphProperty<Sdk?> = propertyGraph.property(null)
  override var pythonSdk: Sdk? by pythonSdkProperty
  override val module: Module?
    get() = intellijModule

  private var intellijModule: Module? = null
  private lateinit var pySdkCreator: PySdkCreator
  private val errorSink: ErrorSink = ShowingMessageErrorSync

  private val projectPathFlows = ProjectPathFlows.create(
    pathProperty.toFlow().combine(nameProperty.toFlow()) { dirPath, projectName ->
      try {
        Path.of(dirPath, projectName).toString()
      }
      catch (_: InvalidPathException) {
        "$dirPath$SystemPathSeparator$projectName"
      }
    }
  )

  override fun setupUI(builder: Panel) {
    val onShowTrigger = object : JComponent() {}
    builder.row { cell(onShowTrigger) }

    val sdkPanelBuilder = PythonSdkPanelBuilderAndSdkCreator(
      onlyAllowedInterpreterTypes = null,
      errorSink = ShowingMessageErrorSync,
      module = null,
    )

    sdkPanelBuilder.buildPanel(builder, projectPathFlows)
    sdkPanelBuilder.onShownInitialization(onShowTrigger)

    this.pySdkCreator = sdkPanelBuilder
  }

  override fun setupProject(project: Project) {
    commitIntellijModule(project)

    val moduleOrProject = when (val module = module) {
      null -> ModuleOrProject.ProjectOnly(project)
      else -> ModuleOrProject.ModuleAndProject(module)
    }

    moduleOrProject.moduleIfExists?.takeIf { createPythonModuleStructure }?.let { module ->
      runWithModalProgressBlocking(project, PyBundle.message("python.sdk.creating.python.module.structure")) {
        pySdkCreator.createPythonModuleStructure(module).onFailure {
          errorSink.emit(it)
        }
      }
    }

    runWithModalProgressBlocking(project, PyBundle.message("python.sdk.creating.python.sdk")) {
      val (sdk, _) = pySdkCreator.getSdk(moduleOrProject).getOr {
        errorSink.emit(it.error)
        return@runWithModalProgressBlocking
      }
      pythonSdk = sdk
      moduleOrProject.moduleIfExists?.let { module ->
        configurePythonSdk(project, module, sdk)
      }
    }
  }

  private fun commitIntellijModule(project: Project) {
    val moduleName = name
    val projectPath = Path.of(path, name)
    val moduleBuilder = PythonModuleTypeBase.getInstance().createModuleBuilder().apply {
      name = moduleName
      contentEntryPath = projectPath.toString()
      moduleFilePath = projectPath.resolve(moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION).toString()
    }
    intellijModule = moduleBuilder.commit(project).firstOrNull()
  }
}
