// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyStatement
import org.jetbrains.annotations.PropertyKey

abstract class PyConvertImportIntentionAction(@PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) intentionText: String) : PyBaseIntentionAction() {

  init {
    text = PyPsiBundle.message(intentionText)
  }

  override fun getFamilyName(): String = text

  fun replaceImportStatement(statement: PyFromImportStatement, file: PsiFile, path: String) {
    val imported = statement.importElements.joinToString(", ") { it.text }

    val generator = PyElementGenerator.getInstance(file.project)
    val languageLevel = LanguageLevel.forElement(file)
    val generatedStatement = generator.createFromImportStatement(languageLevel, path, imported, null)
    val formattedStatement = CodeStyleManager.getInstance(file.project).reformat(generatedStatement)
    statement.replace(formattedStatement)
  }

  fun findStatement(file: PsiFile, editor: Editor): PyFromImportStatement? {
    val position = file.findElementAt(editor.caretModel.offset)
    return PsiTreeUtil.getParentOfType(position, PyFromImportStatement::class.java, true, PyStatement::class.java)
  }
}

