// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import org.jetbrains.annotations.ApiStatus

/**
 * Adds `from __future__ import annotations`, which defers the evaluation of the annotations of the file.
 * Then an annotation can name a class that the file defines later, for example a method that returns its own class.
 */
@ApiStatus.Internal
class PyAddFutureAnnotationsImportQuickFix : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.add.future.annotations.import")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val file = element.containingFile ?: return
    AddImportHelper.addOrUpdateFromImportStatement(file, "__future__", "annotations", null, AddImportHelper.ImportPriority.FUTURE, null)
  }
}
