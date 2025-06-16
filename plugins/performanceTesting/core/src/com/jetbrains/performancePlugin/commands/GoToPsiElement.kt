// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import kotlin.math.max
import kotlin.math.min

class GoToPsiElementWalkingVisitor(private val position: String,
                                   private val actionCallback: ActionCallback,
                                   private val editor: Editor,
                                   private val predicate: (element: PsiElement) -> Boolean) : PsiRecursiveElementWalkingVisitor(true) {
  override fun visitElement(element: PsiElement) {
    if (predicate.invoke(element)) {
      val offset = measureOffset(element, position)
      if (editor.caretModel.offset == offset) {
        actionCallback.setDone()
      }
      else {
        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
      }
      stopWalking()
    }
    super.visitElement(element)
  }

}

fun PsiElement?.goToElement(position: String, actionCallback: ActionCallback, editor: Editor, predicate: (element: PsiElement) -> Boolean) {
  this?.accept(GoToPsiElementWalkingVisitor(position = position, actionCallback = actionCallback, editor = editor, predicate = predicate))
}

private fun measureOffset(element: PsiElement, position: String): Int {
  when (position.lowercase()) {
    "before" -> {
      return element.startOffset
    }
    "after" -> {
      return element.endOffset
    }
    "into_space" -> {
      val spaceIndex = max(1, element.text.indexOf(" "))
      return min(element.endOffset, element.startOffset + spaceIndex)
    }
    else -> {
      return element.startOffset + (element.endOffset - element.startOffset) / 2
    }
  }
}