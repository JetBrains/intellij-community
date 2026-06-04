// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownInlineLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow

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
    val position = super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset,
                                               allowToBeyondMaxPreferredOffset, isSoftWrap)
    if (position < 0) return position
    val documentManager = project?.let { PsiDocumentManager.getInstance(it) } ?: return position
    val file = documentManager.getPsiFile(document) ?: return position

    if (isSoftWrap) {
      if (documentManager.isUncommited(document)) return position
    }
    else {
      documentManager.commitDocument(document)
    }

    val forbidden = runReadAction {
      stopSet.mapNotNull { cls ->
        PsiTreeUtil.findElementOfClassAtOffset(file, position, cls, false)?.takeIf {
          position > it.textRange.startOffset && position < it.textRange.endOffset
        }
      }.minByOrNull { it.textRange.startOffset }
    } ?: return position
    val newEnd = forbidden.textRange.startOffset
    if (newEnd <= startOffset) return -1
    val retry = super.calculateWrapPosition(document, project, startOffset, newEnd,
                                            minOf(maxPreferredOffset, newEnd),
                                            allowToBeyondMaxPreferredOffset, isSoftWrap)
    return if (retry > 0) retry else newEnd
  }

  companion object {
    private val stopSet = setOf(
      MarkdownHeader::class.java, MarkdownInlineLink::class.java, MarkdownLinkDestination::class.java, MarkdownTableCell::class.java,
      MarkdownTableRow::class.java, MarkdownTable::class.java
    )
  }
}