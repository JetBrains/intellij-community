// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class GoToNamedElementCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "goToNamedPsiElement"
    const val SUPPRESS_ERROR_IF_NOT_FOUND: @NonNls String = "SUPPRESS_ERROR_IF_NOT_FOUND"
    val REGEX = " ".toRegex()
    private val COMMENTS_NAMES = setOf("KDoc", "BLOCK_COMMENT", "EOL_COMMENT", "SHEBANG_COMMENT", "DOC_COMMENT", "END_OF_LINE_COMMENT",
                                       "C_STYLE_COMMENT")
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val input = extractCommandArgument(PREFIX)
    val params = input.split(REGEX).dropLastWhile { it.isEmpty() }.toTypedArray()
    val position = params[0]
    val elementName = params[1]
    ApplicationManager.getApplication().invokeLater {
      val editor = FileEditorManager.getInstance(context.project).selectedTextEditor
      val psiFile = PsiDocumentManager.getInstance(context.project).getPsiFile(editor!!.document)
      val caretListener: CaretListener = object : CaretListener {
        override fun caretPositionChanged(e: CaretEvent) {
          actionCallback.setDone()
        }
      }
      editor.caretModel.addCaretListener(caretListener)
      actionCallback.doWhenProcessed { editor.caretModel.removeCaretListener(caretListener) }
      psiFile.goToElement(
        position = position,
        actionCallback = actionCallback,
        editor = editor,
        predicate = {
          elementName == it.text && !hasCommentParent(psiFile!!, it)
        })
      if (params.contains(SUPPRESS_ERROR_IF_NOT_FOUND) && !actionCallback.isDone) {
        actionCallback.setDone()
      }
      else if (!actionCallback.isDone) {
        actionCallback.reject("not found element ${params.contentToString()}")
      }
    }
    return actionCallback.toPromise()
  }

  private fun hasCommentParent(root: PsiElement, element: PsiElement): Boolean {
    var current = element
    while (current != root) {
      if (COMMENTS_NAMES.contains(current.elementType?.debugName) || COMMENTS_NAMES.contains(
          current.elementType.toString()) || COMMENTS_NAMES.contains(current.node.elementType.toString())) {
        return true
      }
      current = current.parent
    }
    return false
  }
}