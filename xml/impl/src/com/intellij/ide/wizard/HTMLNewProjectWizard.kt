// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.PlatformUtils
import com.intellij.util.io.systemIndependentPath

class HTMLNewProjectWizard : LanguageNewProjectWizard {
  override val name: String = "HTML"

  override fun isEnabled(context: WizardContext) = PlatformUtils.isCommunityEdition()

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(private val parent: NewProjectWizardLanguageStep) : AbstractNewProjectWizardStep(parent) {
    override fun setupUI(builder: Panel) {}

    override fun setupProject(project: Project) {
      WebModuleBuilder<Any>().also {
        it.name = parent.name
        it.contentEntryPath = parent.projectPath.systemIndependentPath
      }.commit(project)
    }
  }
}