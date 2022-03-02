// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.*

class MarkdownLineWrapPositionStrategy : GenericLineWrapPositionStrategy() {
  init {
    // We should wrap after space, cause otherwise formatting will eat space once AutoWrapHandler made wrap
    addRule(Rule(' ', WrapCondition.AFTER))
    addRule(Rule('\t', WrapCondition.AFTER))

    // Punctuation.
    addRule(Rule(',', WrapCondition.AFTER))
    addRule(Rule('.', WrapCondition.AFTER))
    addRule(Rule('!', WrapCondition.AFTER))
    addRule(Rule('?', WrapCondition.AFTER))
    addRule(Rule(';', WrapCondition.AFTER))

    // Brackets to wrap after.
    addRule(Rule(')', WrapCondition.AFTER))
    addRule(Rule(']', WrapCondition.AFTER))
    addRule(Rule('}', WrapCondition.AFTER))

    // Brackets to wrap before
    addRule(Rule('(', WrapCondition.BEFORE))
    addRule(Rule('[', WrapCondition.BEFORE))
    addRule(Rule('{', WrapCondition.BEFORE))
  }

  override fun calculateWrapPosition(document: Document, project: Project?, startOffset: Int, endOffset: Int, maxPreferredOffset: Int,
                                     allowToBeyondMaxPreferredOffset: Boolean, isSoftWrap: Boolean): Int {
    val file = project?.let { PsiDocumentManager.getInstance(project).getPsiFile(document) }
               ?: return super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset,
                                                     allowToBeyondMaxPreferredOffset, isSoftWrap)

    if (stopSet.any { PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, it, true) != null }) {
      return -1
    }

    return super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset,
                                       isSoftWrap)
  }

  companion object {
    private val stopSet = setOf(
      MarkdownHeader::class.java, MarkdownLinkDestination::class.java, MarkdownTableCell::class.java,
      MarkdownTableRow::class.java, MarkdownTable::class.java
    )
  }
}