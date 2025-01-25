// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.newProject.steps

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.WebProjectSettingsStepWrapper
import com.intellij.ide.util.projectWizard.WebProjectTemplate
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.observable.util.joinSystemDependentPath
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.PyNewProjectSettings
import com.jetbrains.python.newProject.PythonProjectGenerator
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.newProjectWizard.promotion.PromoProjectGenerator
import com.jetbrains.python.sdk.PyLazySdk
import com.jetbrains.python.sdk.add.v2.PythonAddNewEnvironmentPanel
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JPanel


/**
 * @deprecated Use [com.jetbrains.python.newProjectWizard]
 */
@java.lang.Deprecated(forRemoval = true)
@Deprecated("use com.jetbrains.python.newProjectWizard", level = DeprecationLevel.WARNING)
class PythonProjectSpecificSettingsStep<T : PyNewProjectSettings>(
  projectGenerator: DirectoryProjectGenerator<T>,
  callback: AbstractNewProjectStep.AbstractCallback<T>,
) : ProjectSpecificSettingsStep<T>(projectGenerator, callback), DumbAware {

  private val propertyGraph = PropertyGraph()
  private val projectName = propertyGraph.property("")
  private val projectLocation = propertyGraph.property("")
  private val projectLocationFlowStr = MutableStateFlow(projectLocation.get())
  private val locationHint = propertyGraph.property("").apply {
    dependsOn(projectName, ::updateHint)
    dependsOn(projectLocation, ::updateHint)
  }
  private val createRepository = propertyGraph.property(false)
    .bindBooleanStorage("PyCharm.NewProject.Git")
  private val createScript = propertyGraph.property(false)
    .bindBooleanStorage("PyCharm.NewProject.Welcome")

  init {
    projectLocation.afterChange {
      projectLocationFlowStr.value = projectLocation.get()
    }
  }

  private lateinit var projectNameFiled: JBTextField
  lateinit var mainPanel: DialogPanel
  override fun createAndFillContentPanel(): JPanel {
    if (myProjectGenerator is WebProjectTemplate) {
      peer.buildUI(WebProjectSettingsStepWrapper(this))
    }
    return createContentPanelWithAdvancedSettingsPanel()
  }

  /**
   * Returns the project location that is either:
   * - constructed using two parts (using the values from "Location" and "Name" fields) for Python project types ("Pure Python", "Django",
   *   etc.);
   * - specified directly in the single "Location" field for non-Python project types ("Angular CLI", "Bootstrap", etc.).
   */
  override fun getProjectLocation(): String =
    if (interpreterPanel != null) {
      FileUtil.expandUserHome(projectLocation.joinSystemDependentPath(projectName).get())
    }
    else {
      super.getProjectLocation()
    }

  override fun getRemotePath(): String? {
    return null
  }

  private var interpreterPanel: PythonAddNewEnvironmentPanel? = null

  override fun createBasePanel(): JPanel {
    val projectGenerator = myProjectGenerator
    if (projectGenerator is PromoProjectGenerator) {
      myCreateButton.isEnabled = false
      myLocationField = TextFieldWithBrowseButton()
      return projectGenerator.createPromoPanel()
    }
    if (projectGenerator !is PythonProjectGenerator<*>) return super.createBasePanel()

    val nextProjectName = myProjectDirectory.get()
    projectName.set(nextProjectName.nameWithoutExtension)
    projectLocation.set(nextProjectName.parent)

    // Instead of setting this type as default, we limit types to it
    val onlyAllowedInterpreterTypes = projectGenerator.preferredEnvironmentType?.let { setOf(it) }
    val interpreterPanel = PythonAddNewEnvironmentPanel(ProjectPathFlows.create(projectLocationFlowStr), onlyAllowedInterpreterTypes, errorSink = ShowingMessageErrorSync).also { interpreterPanel = it }

    mainPanel = panel {
      row(message("new.project.name")) {
        projectNameFiled = textField()
          .bindText(projectName)
          .validationOnInput {
            val validationResult = projectGenerator.validate(getProjectLocation())
            if (validationResult.isOk) null else error(validationResult.errorMessage)
          }
          .component
      }
      row(message("new.project.location")) {
        myLocationField = textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
          .bindText(projectLocation)
          .align(Align.FILL)
          .component
      }
      row("") {
        comment("", maxLineLength = 60).bindText(locationHint)
      }
      row("") {
        checkBox(message("new.project.git")).bindSelected(createRepository)
        if (projectGenerator.supportsWelcomeScript()) {
          checkBox(message("new.project.welcome")).bindSelected(createScript)
        }
      }


      panel {
        interpreterPanel.buildPanel(this)
      }
    }

    mainPanel.registerValidators(this) { validations ->
      val anyErrors = validations.entries.any { (key, value) -> key.isVisible && !value.okEnabled }
      val projectLocationValidation = projectGenerator.validate(getProjectLocation())
      myCreateButton.isEnabled = !anyErrors
    }
    myCreateButton.addActionListener { mainPanel.apply() }
    return mainPanel
  }


  private fun updateHint(): String =
    try {
      val projectPath = Path.of(projectLocation.get(), projectName.get())
      message("new.project.location.hint", projectPath)
    }
    catch (e: InvalidPathException) {
      ""
    }

  override fun checkValid(): Boolean {
    if (myProjectGenerator is WebProjectTemplate) {
      return super.checkValid()
    }
    else {
      // todo add proper validation with custom component
      return true
    }
  }

  override fun onPanelSelected() {
    interpreterPanel?.onShown()
  }

  override fun getSdk(): Sdk {
    // It is here only for DS, not used in PyCharm
    return PyLazySdk("Uninitialized environment") { interpreterPanel?.getSdk() }
  }


  override fun getInterpreterInfoForStatistics(): InterpreterStatisticsInfo {
    return interpreterPanel!!.createStatisticsInfo()
  }

  override fun registerValidators() {
    if (myProjectGenerator is PythonProjectGenerator<*>) {
      projectName.afterChange { (myProjectGenerator as PythonProjectGenerator<*>).locationChanged(it) }
    }
  }

  companion object {
    @JvmStatic
    @Deprecated("use PyV3 in com.jetbrains.python.newProjectWizard")
    fun initializeGit(project: Project, root: VirtualFile) {
      runBackgroundableTask(IdeBundle.message("progress.title.creating.git.repository"), project) {
        GitRepositoryInitializer.getInstance()?.initRepository(project, root, true)
      }
    }
  }

}
