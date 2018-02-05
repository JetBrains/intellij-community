// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.java.edu

import com.intellij.facet.ui.ValidationResult
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.NewProjectUtil
import com.intellij.ide.impl.NewProjectUtil.applyJdkToProject
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.JavaSettingsStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.ui.components.JBLabel
import com.jetbrains.python.newProject.ProjectSettingsConfigurer
import java.awt.BorderLayout
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class JavaProjectGenerator : DirectoryProjectGenerator<JavaProjectSettings>, ProjectSettingsConfigurer<JavaProjectSettings>, SettingsStep {
  private val settings = JavaProjectSettings()

  private val moduleBuilder = JavaModuleBuilder()

  private var step: JavaSettingsStep? = null

  private val panel = JPanel(BorderLayout())

  override fun getContext() = settings

  override fun addSettingsField(label: String, field: JComponent) {
    panel.add(JBLabel(label), BorderLayout.WEST)
  }

  override fun addSettingsComponent(component: JComponent) {
  }

  override fun addExpertPanel(panel: JComponent) {
  }

  override fun addExpertField(label: String, field: JComponent) {
  }

  override fun getModuleNameField(): JTextField? = null


  override fun getProjectSettings(): JavaProjectSettings = settings


  override fun extendBasePanel(): JPanel {
    settings.projectBuilder = moduleBuilder

    panel.removeAll()

    step = JavaSettingsStep(this, moduleBuilder,
                            { x -> moduleBuilder.isSuitableSdkType(x) })

    panel.add(step!!.component, BorderLayout.CENTER)

    return panel
  }

  override fun getName(): String = "Java"

  override fun getLogo(): Icon? = AllIcons.Nodes.JavaModule

  override fun generateProject(project: Project, baseDir: VirtualFile, settings: JavaProjectSettings, module: Module) {
    step!!.updateDataModel()

    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        applyJdkToProject(project, settings.projectJdk)
      }
    }, null, null)

    NewProjectUtil.setupCompilerOutputPath(project, File(baseDir.canonicalPath, "out").absolutePath)
    settings.projectBuilder!!.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
  }

  override fun validate(baseDirPath: String): com.intellij.facet.ui.ValidationResult {
    step!!.validate()
    return ValidationResult.OK
  }
}

class JavaProjectSettings : WizardContext(null, null)
