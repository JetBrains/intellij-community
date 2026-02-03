// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.inspections

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLQuotedText

class YAMLAddQuoteQuickFix(scalarOrTemplate: PsiElement, private val quickFixText: @Nls String, private val singleQuote: Boolean = false) :
  PsiUpdateModCommandAction<PsiElement>(scalarOrTemplate) {
  override fun getFamilyName(): @Nls String = quickFixText

  override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
    wrapWithQuotes(element, singleQuote)
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