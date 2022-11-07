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
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import kotlin.math.max
import kotlin.math.min

class GoToAnyPsiElement(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "goToAnyPsiElement"
    const val SUPPRESS_ERROR_IF_NOT_FOUND: @NonNls String = "SUPPRESS_ERROR_IF_NOT_FOUND"
    val REGEX = " ".toRegex()
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val input = extractCommandArgument(GoToCommand.PREFIX)
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
      psiFile?.accept(object : PsiRecursiveElementWalkingVisitor(true) {
        override fun visitElement(element: PsiElement) {
          if (params.contains(element.elementType?.debugName)) {
            val spaceIndex = max(1, element.text.indexOf(" "))
            val offset = min(element.endOffset, element.startOffset + spaceIndex)
            if (editor.caretModel.offset == offset) {
              actionCallback.setDone()
            } else {
              editor.caretModel.moveToOffset(offset)
            }
            stopWalking()
          }
          super.visitElement(element)
        }
      })
      if (params.contains(SUPPRESS_ERROR_IF_NOT_FOUND) && !actionCallback.isDone) {
         actionCallback.setDone()
      } else if (!actionCallback.isDone) {
        actionCallback.reject("not found any element")
      }
    }
    return actionCallback.toPromise()
  }
}