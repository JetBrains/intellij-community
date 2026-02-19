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
import com.intellij.psi.util.elementType
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class GoToNextPsiElement(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "goToNextPsiElement"
    const val SUPPRESS_ERROR_IF_NOT_FOUND: @NonNls String = "SUPPRESS_ERROR_IF_NOT_FOUND"
    val REGEX = " ".toRegex()
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val input = extractCommandArgument(PREFIX)
    val params = input.split(REGEX).dropLastWhile { it.isEmpty() }.toSet()
    ApplicationManager.getApplication().invokeLater {
      val project = context.project
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor!!.document)
      val caretListener: CaretListener = object : CaretListener {
        override fun caretPositionChanged(e: CaretEvent) {
          actionCallback.setDone()
        }
      }
      editor.caretModel.addCaretListener(caretListener)
      actionCallback.doWhenProcessed { editor.caretModel.removeCaretListener(caretListener) }
      psiFile.goToElement(position = "into_space", actionCallback = actionCallback, editor = editor,
                          predicate = { params.contains(it.elementType?.debugName) })
      if (params.contains(SUPPRESS_ERROR_IF_NOT_FOUND) && !actionCallback.isDone) {
        actionCallback.setDone()
      }
      else if (!actionCallback.isDone) {
        actionCallback.reject("not found any element")
      }
    }
    return actionCallback.toPromise()
  }
}