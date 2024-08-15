// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.steps

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.getUserHomeProjectDir
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.WebProjectSettingsStepWrapper
import com.intellij.ide.util.projectWizard.WebProjectTemplate
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.application.PathManager
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
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.PyNewProjectSettings
import com.jetbrains.python.newProject.PythonProjectGenerator
import com.jetbrains.python.newProject.PythonPromoProjectGenerator
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.sdk.PyLazySdk
import com.jetbrains.python.sdk.add.v2.PythonAddNewEnvironmentPanel
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.*
import javax.swing.JPanel

class PythonProjectSpecificSettingsStep<T: PyNewProjectSettings>(projectGenerator: DirectoryProjectGenerator<T>,
                                           callback: AbstractNewProjectStep.AbstractCallback<T>)
  : ProjectSpecificSettingsStep<T>(projectGenerator, callback), DumbAware {

  private val propertyGraph = PropertyGraph()
  private val projectName = propertyGraph.property("")
  private val projectLocation = propertyGraph.property("")
  private val locationHint = propertyGraph.property("").apply {
    dependsOn(projectName, ::updateHint)
    dependsOn(projectLocation, ::updateHint)
  }
  private val createRepository = propertyGraph.property(false)
    .bindBooleanStorage("PyCharm.NewProject.Git")
  private val createScript = propertyGraph.property(false)
    .bindBooleanStorage("PyCharm.NewProject.Welcome")

  private lateinit var projectNameFiled: JBTextField
  lateinit var mainPanel: DialogPanel
  override fun createAndFillContentPanel(): JPanel {
    if (myProjectGenerator is WebProjectTemplate) {
      peer.buildUI(WebProjectSettingsStepWrapper(this))
    }
    return createContentPanelWithAdvancedSettingsPanel()
  }

  @RequiresEdt
  override fun createWelcomeScript(): Boolean  = createScript.get()

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
    if (projectGenerator is PythonPromoProjectGenerator) {
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
    val interpreterPanel = PythonAddNewEnvironmentPanel(projectLocation.joinSystemDependentPath(projectName), onlyAllowedInterpreterTypes).also { interpreterPanel = it }

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
     val uiCustomizer =  projectGenerator.mainPartUiCustomizer
      row("") {
        checkBox(message("new.project.git")).bindSelected(createRepository)
        if (projectGenerator.supportsWelcomeScript()) {
          checkBox(message("new.project.welcome")).bindSelected(createScript)
        }
        uiCustomizer?.checkBoxSection(this)
      }

      uiCustomizer?.let {
          uiCustomizer.underCheckBoxSection(this)
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


  override fun findSequentNonExistingUntitled(): File {
    return Optional
      .ofNullable(PyUtil.`as`(myProjectGenerator, PythonProjectGenerator::class.java))
      .map { it.newProjectPrefix }
      .map { FileUtil.findSequentNonexistentFile(getBaseDir(), it!!, "") }
      .orElseGet { super.findSequentNonExistingUntitled() }
  }

  private fun getBaseDir(): File {
    if (PlatformUtils.isDataSpell() && Path.of(ProjectUtil.getBaseDir()).startsWith(PathManager.getConfigDir())) {
      return File(getUserHomeProjectDir())
    }
    return File(ProjectUtil.getBaseDir())
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

  override fun installFramework(): Boolean {
    return true
  }

  override fun onPanelSelected() {
    interpreterPanel?.onShown()
  }

  override fun getSdk(): Sdk {
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
    fun initializeGit(project: Project, root: VirtualFile) {
      runBackgroundableTask(IdeBundle.message("progress.title.creating.git.repository"), project) {
        GitRepositoryInitializer.getInstance()?.initRepository(project, root, true)
      }
    }
  }

}
