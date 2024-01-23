// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java

import com.intellij.icons.AllIcons
import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.jetbrains.python.newProject.NewPythonProjectStep

/**
 * A wizard for creating new pure-Python projects in IntelliJ.
 *
 * It suggests creating a new Python virtual environment for your new project to follow Python best practices.
 */
internal class PythonNewProjectWizard : LanguageGeneratorNewProjectWizard {

  override val name = NewProjectWizardConstants.Language.PYTHON

  override val icon = AllIcons.Language.Python

  override val ordinal = 600

  override fun createStep(parent: NewProjectWizardStep): NewProjectWizardStep = NewPythonProjectStep(parent)
}

