// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.InplaceRefactoringContinuation
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.rename.Renamer
import com.intellij.refactoring.rename.RenamerFactory
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.context.Context
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise


class StartInlineRenameCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val SPAN_NAME = "startInlineRename"
    const val PREFIX = "${CMD_PREFIX}startInlineRename"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(Runnable {
      val focusedComponent = IdeFocusManager.findInstance().focusOwner
      val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
      val editor = dataContext.getData(CommonDataKeys.EDITOR)
      if (editor == null) {
        actionCallback.reject("Editor is not focused")
        return@Runnable
      }
      if (InplaceRefactoringContinuation.tryResumeInplaceContinuation(project, editor, RenameElementAction::class.java)) {
        actionCallback.reject("Another refactoring is in progress")
        return@Runnable
      }

      if (!PsiDocumentManager.getInstance(project).commitAllDocumentsUnderProgress()) {
        actionCallback.reject("Can't commit documents")
        return@Runnable
      }

      val renamers: List<Renamer> = RenamerFactory.EP_NAME.extensionList.flatMap { factory: RenamerFactory ->
        factory.createRenamers(dataContext)
      }
      if (renamers.isEmpty()) {
        actionCallback.reject("Renamers are empty")
      }
      else if (renamers.size == 1) {
        PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).use {
          renamers[0].performRename()
          actionCallback.setDone()
        }
      }
      else {
        actionCallback.reject("There are too many renamers")
      }
    }))
    return actionCallback.toPromise()
  }
}