// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyStringLiteralExpression
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyEscapeBackslashQuickFix(private val offsetInElement: Int) : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.escape.backslash")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    if (element !is PyStringLiteralExpression) return

    if (offsetInElement >= element.textLength || element.text[offsetInElement] != '\\') return

    val newText = StringBuilder(element.text).insert(offsetInElement, "\\").toString()

    val newElement = PyElementGenerator.getInstance(project)
      .createStringLiteralAlreadyEscaped(newText)
    element.replace(newElement)
  }
}
