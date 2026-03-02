// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.frontend.commands

import com.intellij.ide.actions.ViewStructureAction
import com.intellij.ide.util.FileStructurePopup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.platform.structureView.impl.StructureViewScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.util.function.Consumer
import javax.swing.tree.TreePath

class ShowFileStructurePopupCommand(text: String, line: Int) : AbstractCommand(text, line), Disposable {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(Runnable {
      val project = context.getProject()
      var fileEditor = FileEditorManager.getInstance(project).getSelectedEditor()
      //fallback for the remote case to avoid changing the default monolith behavior
      if (fileEditor == null) {
        val editors = FileEditorManager.getInstance(project).selectedEditorWithRemotes
        if (editors.size > 1) {
          actionCallback.reject("Too many selected editors")
          return@Runnable
        }
        fileEditor = editors.iterator().next()
      }
      if (fileEditor != null) {
        val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).startSpan()
        span.makeCurrent().use {
          val popup = ViewStructureAction.createPopupForTest(project, fileEditor)
          when (popup) {
            is FileStructurePopup -> {
              val spanShow = PerformanceTestSpan.TRACER.spanBuilder("$SPAN_NAME#Show").startSpan()
              val spanFill = PerformanceTestSpan.TRACER.spanBuilder("$SPAN_NAME#Fill").startSpan()
              popup.showWithResult().onProcessed(Consumer { path: TreePath? ->
                actionCallback.setDone()
                spanFill.end()
                span.end()
              })
              spanShow.end()
            }
            is com.intellij.platform.structureView.frontend.FileStructurePopup -> {
              val cs = StructureViewScopeHolder.getInstance(project).cs.childScope("$this scope")
              val spanShow = PerformanceTestSpan.TRACER.spanBuilder("$SPAN_NAME#Show").startSpan()
              val spanFill = PerformanceTestSpan.TRACER.spanBuilder("$SPAN_NAME#Fill").startSpan()
              popup.show()
              spanShow.end()
              cs.launch(Dispatchers.Default) {
                popup.waitUpdateFinished()
                spanFill.end()
                span.end()
                actionCallback.setDone()
              }
            }
            else -> {
              span.setStatus(StatusCode.ERROR, "File structure popup is null")
              actionCallback.reject("File structure popup is null")
            }
          }
        }
      }
      else {
        actionCallback.reject("File editor is null")
      }
    }))
    return actionCallback.toPromise()
  }

  override fun dispose() {
  }

  companion object {
    @JvmField
    val PREFIX: String = CMD_PREFIX + "showFileStructureDialog"
    const val SPAN_NAME: String = "FileStructurePopup"
  }
}