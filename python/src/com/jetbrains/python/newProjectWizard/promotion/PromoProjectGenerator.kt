// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.promotion

import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import javax.swing.JPanel

/**
 * [isPython] shows this promotion in "python" section ("other" otherwise)
 */
abstract class PromoProjectGenerator(val isPython: Boolean) : DirectoryProjectGenerator<PyV3BaseProjectSettings> {
  abstract fun createPromoPanel(): JPanel

  override fun generateProject(project: Project, baseDir: VirtualFile, settings: PyV3BaseProjectSettings, module: Module) = Unit

  override fun validate(baseDirPath: String): ValidationResult = ValidationResult.OK
}