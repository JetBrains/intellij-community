// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import com.jetbrains.python.extensions.getQName
import com.jetbrains.python.psi.PyFile
import org.jetbrains.annotations.ApiStatus

/**
 * Converts location of 'from .three import foo' to absolute path, e.g. 'from one.two.three import foo'.
 *
 * @see PyRelativeToAbsoluteImportIntention
 * @author Aleksei.Kniazev
 */
@ApiStatus.Internal
class PyRelativeToAbsoluteImportIntention : PyConvertImportIntentionAction("INTN.convert.relative.to.absolute") {
  override fun getPresentation(context: ActionContext, element: PsiElement): Presentation? {
    if (context.file !is PyFile) return null
    val statement = findStatement(element) ?: return null
    return if (statement.relativeLevel > 0) super.getPresentation(context, element) else null
  }

  override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
    val statement = findStatement(element) ?: return
    val source = statement.resolveImportSource() ?: return
    val qName = source.getQName()

    replaceImportStatement(statement, context.file, qName.toString())
  }
}