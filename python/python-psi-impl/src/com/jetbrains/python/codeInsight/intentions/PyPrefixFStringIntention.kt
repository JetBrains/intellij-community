// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStringElement

/**
 * Prefixes a string literal with "f" to make it an f-string literal (for Python versions >= 3.6).
 *
 * @author Daniel Schmidt
 */
class PyPrefixFStringIntention : PyBaseIntentionAction() {

  companion object {
    val OPEN_CURLY_BRACES = "\\{+".toRegex()
  }

  init {
    text = PyPsiBundle.message("INTN.prefix.fstring")
  }

  override fun getFamilyName(): String = text

  override fun doInvoke(project: Project, editor: Editor, file: PsiFile) {
    finStringElementAtCaret(file, editor)?.apply {
      val expression = PyElementGenerator.getInstance(project).createStringLiteralAlreadyEscaped("f${text}")
      replace(expression.stringElements.first())
    }
  }

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    if (file !is PyFile) return false
    finStringElementAtCaret(file, editor)?.let { it ->
      return OPEN_CURLY_BRACES.findAll(it.text).any { it.value.length % 2 == 1 }
    }
    return false
  }

  private fun finStringElementAtCaret(file: PsiFile, editor: Editor): PyStringElement? {
    // offset - 1: to allow prefixing the following case -> 'age: {x}'<caret>
    return file.findElementAt(editor.caretModel.offset - 1).let {
      if ((it !is PyStringElement) ||
          (!it.isTerminated) ||
          (it.elementType == PyTokenTypes.DOCSTRING) ||
          (it.isFormatted || it.isUnicode || it.isBytes) ||
          (LanguageLevel.forElement(it).isOlderThan(LanguageLevel.PYTHON36))) null
      else it
    }
  }
}