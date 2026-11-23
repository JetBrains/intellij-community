// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject

import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.platform.util.progress.withProgressText
import com.jetbrains.python.project.asPyProjectOrCandidate
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GitNewProjectWizardData.Companion.gitData
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.DEFAULT_EEL_FOR_NEW_PROJECTS
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.onFailure
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PySdkCreator
import com.jetbrains.python.sdk.add.v2.PythonSdkPanelBuilderAndSdkCreator
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.runWithSdkConfigurationLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.io.files.SystemPathSeparator
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import org.jetbrains.annotations.ApiStatus

/**
 * Data for sharing among the steps of the new Python project wizard.
 */
interface NewProjectWizardPythonData : NewProjectWizardBaseData {

  /**
   * The interpreter of the new Python project or module. It is `null` until [NewProjectWizardStep.setupProject] gets or
   * creates the interpreter and sets it on the module.
   */
  @get:ApiStatus.Internal
  val pythonInterpreter: PythonInterpreter?

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
class NewPythonProjectStep(parent: NewProjectWizardStep, val createPythonModuleStructure: Boolean) : AbstractNewProjectWizardStep(parent),
                                                                                                     NewProjectWizardBaseData by parent.baseData!!,
                                                                                                     NewProjectWizardPythonData {
  constructor(parent: NewProjectWizardStep) : this(parent,
                                                   false) // a separated constructor was made for compatibility with existing plugins

  @get:ApiStatus.Internal
  override var pythonInterpreter: PythonInterpreter? = null
    private set
  override val module: Module?
    get() = intellijModule

  private var intellijModule: Module? = null
  private lateinit var pySdkCreator: PySdkCreator
  private val errorSink: ErrorSink = ErrorSink()

  /**
   * We only support new projets on local eel in Idea. To create remote projects, use Pycharm.
   */
  private val theOnlySupportedEel = DEFAULT_EEL_FOR_NEW_PROJECTS
  private val projectPathFlows = ProjectPathFlows.create(
    pathProperty.toFlow().combine(nameProperty.toFlow()) { dirPath, projectName ->
      try {
        Path.of(dirPath, projectName).toString()
      }
      catch (_: InvalidPathException) {
        "$dirPath$SystemPathSeparator$projectName"
      }
    }, onlyAllowPathsOn = theOnlySupportedEel.descriptor
  )

  override fun setupUI(builder: Panel) {
    val onShowTrigger = object : JComponent() {}
    builder.row { cell(onShowTrigger) }

    val sdkPanelBuilder = PythonSdkPanelBuilderAndSdkCreator(eel = theOnlySupportedEel)

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

    // The tool creates its project files and the environment, and the interpreter is set on the module, under one lock.
    runWithSdkConfigurationLock(project) {
      moduleOrProject.moduleIfExists?.takeIf { createPythonModuleStructure }?.let { module ->
        // What the shared Git step of this wizard was left at, so that a tool scaffolding the project does not create a
        // repository against it (PY-92436). Absent step — no git4idea, or an added module rather than a new project —
        // reads as unchecked, which is what the wizard itself would then do.
        val createGitRepository = gitData?.git == true
        withProgressText(PyBundle.message("python.sdk.creating.python.module.structure")) {
          pySdkCreator.createPythonModuleStructure(module, createGitRepository).onFailure {
            errorSink.emit(it, project)
          }
        }
      }
      val (pythonInterpreter, _) = pySdkCreator.getSdk(moduleOrProject).getOr {
        errorSink.emit(it.error, project)
        return@runWithSdkConfigurationLock
      }
      // The interpreter the project then has: the registry can adopt an existing SDK for it.
      val set = moduleOrProject.moduleIfExists?.asPyProjectOrCandidate()?.setPythonInterpreter(pythonInterpreter)?.getOr {
        errorSink.emit(it.error, project)
        return@runWithSdkConfigurationLock
      }
      this@NewPythonProjectStep.pythonInterpreter = set ?: pythonInterpreter
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
