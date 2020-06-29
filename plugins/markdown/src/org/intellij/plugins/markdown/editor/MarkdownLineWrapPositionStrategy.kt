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
  }

  override fun calculateWrapPosition(document: Document,
                                     project: Project?,
                                     startOffset: Int,
                                     endOffset: Int,
                                     maxPreferredOffset: Int,
                                     allowToBeyondMaxPreferredOffset: Boolean,
                                     isSoftWrap: Boolean): Int {
    if (project == null) {
      return super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset,
                                         isSoftWrap)
    }

    val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
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
      MarkdownHeaderImpl::class.java, MarkdownLinkDestinationImpl::class.java, MarkdownTableCellImpl::class.java,
      MarkdownTableRowImpl::class.java, MarkdownTableImpl::class.java
    )
  }
}