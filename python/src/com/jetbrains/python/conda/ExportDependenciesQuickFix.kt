// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.conda

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.DependenciesExporter

internal class ExportDependenciesQuickFix(private val dependenciesExporter: DependenciesExporter) : LocalQuickFix {
  override fun getFamilyName(): @IntentionFamilyName String {
    return PyBundle.message("QFIX.NAME.export.dependencies")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement.containingFile ?: return

    dependenciesExporter.export(file)
  }

  override fun startInWriteAction(): Boolean = false
}
