// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardStepPanel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A base adapter class to turn a [GeneratorNewProjectWizard] into a
 * [com.intellij.platform.DirectoryProjectGenerator] and register as an extension point.
 *
 * @see NewProjectWizardProjectSettingsStep
 */
open class NewProjectWizardDirectoryGeneratorAdapter<T : Any>(val wizard: GeneratorNewProjectWizard) : DirectoryProjectGeneratorBase<T>() {
  internal lateinit var panel: NewProjectWizardStepPanel

  @Suppress("DialogTitleCapitalization")
  override fun getName(): String = wizard.name
  override fun getLogo(): Icon = wizard.icon

  override fun generateProject(project: Project, baseDir: VirtualFile, settings: T, module: Module) {
    panel.step.setupProject(project)
  }

  override fun createPeer(): ProjectGeneratorPeer<T> {
    val context = WizardContext(null) {}
    return object : GeneratorPeerImpl<T>() {
      override fun getComponent(): JComponent {
        panel = NewProjectWizardStepPanel(wizard.createStep(context))
        return panel.component
      }
    }
  }
}

/**
 * A wizard-enabled project settings step that you should use for your [projectGenerator] in your
 * [AbstractNewProjectStep.Customization.createProjectSpecificSettingsStep] to provide the project wizard UI and actions.
 */
class NewProjectWizardProjectSettingsStep<T : Any>(private val projectGenerator: NewProjectWizardDirectoryGeneratorAdapter<T>)
  : ProjectSettingsStepBase<T>(projectGenerator, null) {

  init {
    myCallback = AbstractNewProjectStep.AbstractCallback()
  }

  override fun createAndFillContentPanel(): JPanel =
    JPanel(VerticalFlowLayout()).apply {
      add(peer.component)
    }

  override fun registerValidators() {}

  override fun getProjectLocation(): String =
    projectGenerator.panel.step.context.projectFileDirectory

  override fun getActionButton(): JButton =
    super.getActionButton().apply {
      addActionListener {
        projectGenerator.panel.apply()
      }
    }
}