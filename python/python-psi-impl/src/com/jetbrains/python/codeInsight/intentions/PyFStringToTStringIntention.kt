// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.*

/**
 * Intention to convert between f-strings and t-strings (PEP-750)
 */
class PyFStringToTStringIntention : PsiUpdateModCommandAction<PyFormattedStringElement>(PyFormattedStringElement::class.java) {
  override fun getPresentation(context: ActionContext, stringElement: PyFormattedStringElement): Presentation? {
    if (LanguageLevel.forElement(stringElement).isOlderThan(LanguageLevel.PYTHON314)) return null
    
    val stringLiteral = stringElement.getParent() as? PyStringLiteralExpression ?: return null
    val docStringOwner = stringLiteral.parentOfType<PyDocStringOwner>()
    if (docStringOwner != null && docStringOwner.getDocStringExpression() === stringLiteral) return null

    if (stringElement.isFormatted) {
      return Presentation.of(PyPsiBundle.message("INTN.convert.f.string.to.t.string"))
    }
    else if (stringElement.isTemplate) {
      return Presentation.of(PyPsiBundle.message("INTN.convert.t.string.to.f.string"))
    }
    return null
  }

  override fun getFamilyName(): String = PyPsiBundle.message("INTN.NAME.convert.between.f.string.t.string")

  override fun invoke(context: ActionContext, stringElement: PyFormattedStringElement, updater: ModPsiUpdater) {
    val prefixOffset = stringElement.textOffset
    val oldPrefix = stringElement.prefix
    val newPrefix = when {
      oldPrefix.contains("f") || oldPrefix.contains("F") -> oldPrefix.replace("f", "t").replace("F", "T")
      oldPrefix.contains("t") || oldPrefix.contains("T") -> oldPrefix.replace("t", "f").replace("T", "F")
      else -> return
    }
    PyUtil.updateDocumentUnblockedAndCommitted(stringElement) { document: Document ->
      document.replaceString(prefixOffset, prefixOffset + oldPrefix.length, newPrefix)
    }
  }
}
