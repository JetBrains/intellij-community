// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.liveTemplates

import com.intellij.codeInsight.template.impl.TemplatePreprocessor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharArrayUtil

class PyDecoratorTemplatePreprocessor : TemplatePreprocessor {
  override fun preprocessTemplate(editor: Editor, file: PsiFile, caretOffset: Int, textToInsert: String, templateText: String) {
    if (!templateText.startsWith('@')) {
      return
    }

    val document: Document = editor.getDocument()
    if (caretOffset <= 0) {
      return
    }

    val text: CharSequence = document.charsSequence
    val checkPos = CharArrayUtil.shiftBackward(text, caretOffset - 1, " \t")

    if (checkPos >= 0 && text[checkPos] == '@') {
      WriteAction.run<RuntimeException> {
        document.deleteString(checkPos, checkPos + 1)
      }
    }
  }
}
