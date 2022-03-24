// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.PlatformUtils
import com.intellij.xml.XmlBundle

class HTMLNewProjectWizard : LanguageNewProjectWizard {
  override val name: String = "HTML"
  override val ordinal = 400

  override fun isEnabled(context: WizardContext) = PlatformUtils.isCommunityEdition()

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(private val parent: NewProjectWizardLanguageStep) : AbstractNewProjectWizardStep(parent) {

    private val addSampleCode = propertyGraph.property(false)

    override fun setupUI(builder: Panel) {
      with(builder) {
        row {
          checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
            .bindSelected(addSampleCode)
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = WebModuleBuilder<Any>().also {
        it.name = parent.name
        it.contentEntryPath = "${parent.path}/${parent.name}"
      }
      builder.commit(project)

      if (!addSampleCode.get()) return

      StartupManager.getInstance(project).runWhenProjectIsInitialized {
        val contentEntryPath = builder.contentEntryPath ?: return@runWhenProjectIsInitialized
        val root = LocalFileSystem.getInstance().findFileByPath(contentEntryPath)
        if (root == null) return@runWhenProjectIsInitialized

        WriteCommandAction.runWriteCommandAction(
          project, null, null,
          Runnable {
            val fileTemplate = FileTemplateManager.getInstance(project).getInternalTemplate(
              FileTemplateManager.INTERNAL_HTML5_TEMPLATE_NAME)
            val directory = PsiManager.getInstance(project).findDirectory(root) ?: return@Runnable
            CreateFileFromTemplateAction.createFileFromTemplate(
              XmlBundle.message("html.action.new.file.name"),
              fileTemplate,
              directory,
              null,
              true
            )
          })
      }
    }
  }
}