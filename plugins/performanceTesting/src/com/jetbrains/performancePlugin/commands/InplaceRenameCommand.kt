package com.jetbrains.performancePlugin.commands

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.InplaceRefactoringContinuation
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.rename.Renamer
import com.intellij.refactoring.rename.RenamerFactory
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class InplaceRenameCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = "${CMD_PREFIX}renameInplace"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    ApplicationManager.getApplication().invokeAndWait {
      val focusedComponent = IdeFocusManager.findInstance().focusOwner
      val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
      val editor = dataContext.getData(CommonDataKeys.EDITOR)
      if(editor == null){
        actionCallback.reject("Editor is not focused")
        return@invokeAndWait
      }
      if (InplaceRefactoringContinuation.tryResumeInplaceContinuation(project, editor, RenameElementAction::class.java)) {
        actionCallback.reject("Another refactoring is in progress")
        return@invokeAndWait
      }

      if (!PsiDocumentManager.getInstance(project).commitAllDocumentsUnderProgress()) {
        actionCallback.reject("Can't commit documents")
        return@invokeAndWait
      }

      val renamers: List<Renamer> = RenamerFactory.EP_NAME.extensionList.flatMap { factory: RenamerFactory ->
        factory.createRenamers(dataContext)
      }
      if (renamers.isEmpty()) {
        actionCallback.reject("Renamers are empty")
        return@invokeAndWait
      }
      else if (renamers.size == 1) {
        renamers[0].performRename()
        actionCallback.setDone()
      }
    }

    return actionCallback.toPromise()
  }
}