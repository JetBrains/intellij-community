// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intentions

import com.intellij.openapi.editor.actions.lists.DefaultListSplitJoinContext
import com.intellij.openapi.editor.actions.lists.JoinOrSplit
import com.intellij.openapi.editor.actions.lists.ListWithElements
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlBundle

private class XmlAttributesSplitJoinContext : DefaultListSplitJoinContext() {
  override fun extractData(context: PsiElement): ListWithElements? {
    val attr = PsiTreeUtil.getParentOfType(context, XmlAttribute::class.java) ?: return null
    val tag = PsiTreeUtil.getParentOfType(attr, XmlTag::class.java) ?: return null

    return ListWithElements(tag, tag.attributes.toList())
  }

  override fun reformatRange(file: PsiFile, rangeToAdjust: TextRange, split: JoinOrSplit) {
    when (split) {
      JoinOrSplit.JOIN -> super.reformatRange(file, rangeToAdjust, split)
      //infer position of the first element using formatter, instead of direct code style settings 
      JoinOrSplit.SPLIT -> CodeStyleManager.getInstance(file.project).reformatText(file, rangeToAdjust.startOffset, rangeToAdjust.endOffset)
    }
  }

  override fun isSeparator(element: PsiElement): Boolean = false
  override fun getHeadBreakJoinReplacement(firstElement: PsiElement): String = " "
  override fun getTailBreakJoinReplacement(lastElement: PsiElement): String = if (PsiTreeUtil.skipWhitespacesForward(lastElement) is LeafElement) "" else " "
  override fun getSplitText(data: ListWithElements): String = XmlBundle.message("intention.name.split.attributes")
  override fun getJoinText(data: ListWithElements): String = XmlBundle.message("intention.name.join.attributes")
}