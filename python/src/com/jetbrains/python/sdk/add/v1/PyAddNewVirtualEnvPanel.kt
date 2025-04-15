// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addBaseInterpretersAsync
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.createVirtualEnvAndSdkSynchronously
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.event.DocumentEvent

open class PyAddNewVirtualEnvPanel(private val project: Project?,
                                   private val module: Module?,
                                   private val existingSdks: List<Sdk>,
                                   newProjectPath: String?,
                                   private val context: UserDataHolder) : PyAddNewEnvPanel() {
  override val envName: String = "Virtualenv"

  override var newProjectPath: String? = newProjectPath
    set(value) {
      field = value
      pathField.text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
    }

  val path: String
    get() = pathField.text.trim()

  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.new.environment")
  override val icon: Icon = PythonIcons.Python.Virtualenv
  protected val baseSdkField = PySdkPathChoosingComboBox()
  protected val pathField = TextFieldWithBrowseButton().apply {
    text = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
    addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(PySdkBundle.message("python.venv.location.chooser")))
  }
  val inheritSitePackagesField = JBCheckBox(PyBundle.message("sdk.create.venv.dialog.label.inherit.global.site.packages"))
  private val makeSharedField = JBCheckBox(PyBundle.message("available.to.all.projects"))

  init {
    layoutComponents()
    addBaseInterpretersAsync(baseSdkField, existingSdks, module, context)
  }

  protected open fun layoutComponents() {
    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PySdkBundle.message("python.venv.location.label"), pathField)
      .addLabeledComponent(PySdkBundle.message("python.venv.base.label"), baseSdkField)
      .addComponent(inheritSitePackagesField)
      .addComponent(makeSharedField)
      .panel
    add(formPanel, BorderLayout.NORTH)
  }

  override fun validateAll(): List<ValidationInfo> =
    listOfNotNull(validateEnvironmentDirectoryLocation(pathField, PlatformAndRoot.local),
                  validateSdkComboBox(baseSdkField, this))

  override fun getOrCreateSdk(): Sdk? {
    return createVirtualEnvAndSdkSynchronously(baseSdkField.selectedSdk, existingSdks, pathField.text, newProjectPath, project, module, context,
                                               inheritSitePackagesField.isSelected, makeSharedField.isSelected)
  }

  override fun getStatisticInfo(): InterpreterStatisticsInfo? {
    return InterpreterStatisticsInfo(InterpreterType.VIRTUALENV,
                                     InterpreterTarget.LOCAL,
                                     inheritSitePackagesField.isSelected,
                                     makeSharedField.isSelected,
                                     false)
  }

  override fun addChangeListener(listener: Runnable) {
    pathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        listener.run()
      }
    })
    baseSdkField.childComponent.addItemListener { listener.run() }
  }

  private val projectBasePath: @SystemIndependent String?
    get() = newProjectPath ?: module?.basePath ?: project?.basePath
}
