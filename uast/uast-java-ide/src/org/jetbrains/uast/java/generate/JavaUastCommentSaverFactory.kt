// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java.generate

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.siyeh.ig.psiutils.CommentTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.generate.UastCommentSaverFactory
import org.jetbrains.uast.toUElement

@ApiStatus.Experimental
internal class JavaUastCommentSaverFactory : UastCommentSaverFactory {

  override val language: Language
    get() = JavaLanguage.INSTANCE

  override fun grabComments(firstResultUElement: UElement, lastResultUElement: UElement?): UastCommentSaverFactory.UastCommentSaver? {
    val firstSourcePsiElement = firstResultUElement.sourcePsi ?: return null
    val lastSourcePsiElement = lastResultUElement?.sourcePsi ?: firstSourcePsiElement
    val commentTracker = CommentTracker()
    var e = firstSourcePsiElement
    commentTracker.grabComments(e)
    while (e !== lastSourcePsiElement) {
      e = e.getNextSibling() ?: break
      commentTracker.grabComments(e)
    }

    return object : UastCommentSaverFactory.UastCommentSaver {
      override fun restore(firstResultUElement: UElement, lastResultUElement: UElement?) {
        var target: PsiElement? = firstResultUElement.sourcePsi ?: return
        while (target?.parent.toUElement()?.sourcePsi == firstResultUElement.sourcePsi) {
          target = target?.parent
        }
        if (target != null) {
          commentTracker.insertCommentsBefore(target)
        }
      }

      override fun markUnchanged(firstResultUElement: UElement?, lastResultUElement: UElement?) {
        val firstPsiElement = firstResultUElement?.sourcePsi ?: return
        val lastPsiElement = lastResultUElement?.sourcePsi ?: firstPsiElement
        commentTracker.markRangeUnchanged(firstPsiElement, lastPsiElement)
      }
    }
  }
}