// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.inspections

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLQuotedText

class YAMLAddQuoteQuickFix(scalarOrTemplate: PsiElement, private val quickFixText: @Nls String, private val singleQuote: Boolean = false) :
  LocalQuickFixAndIntentionActionOnPsiElement(scalarOrTemplate) {
  override fun getText(): @Nls String = quickFixText

  override fun getFamilyName(): @Nls String = text

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    wrapWithQuotes(startElement, singleQuote)
  }
}

fun wrapWithQuotes(startElement: PsiElement, singleQuote: Boolean) {
  val quote = if (singleQuote) '\'' else '"'
  val text = """
          key: $quote${startElement.text}$quote
        """.trimIndent()
  val tempFile = YAMLElementGenerator.getInstance(startElement.project).createDummyYamlWithText(text)
  val quoted = PsiTreeUtil.collectElementsOfType(tempFile, YAMLQuotedText::class.java).firstOrNull() ?: return
  startElement.replace(quoted)
}