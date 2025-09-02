// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.api.trace.Span
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise


class FinishInlineRename(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val SPAN_NAME = "performInlineRename"
    const val PREPARE_SPAN_NAME = "prepareForRename"
    const val PREFIX = "${CMD_PREFIX}finishInlineRename"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val connect = context.project.messageBus.connect()
    var preparingSpan: Span? = null
    connect.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
      var refactoringSpan: Span? = null
      override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        preparingSpan?.end()
        refactoringSpan = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext()).startSpan()
      }

      override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        refactoringSpan?.end()
        connect.disconnect()
        actionCallback.setDone()
      }

      override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {
        actionCallback.reject("Conflict was detected")
      }
    })
    connect.subscribe(CommandListener.TOPIC, object : CommandListener {
      fun executeIfRequiredEvent(event: CommandEvent, apply: () -> Unit) {
        val enclosingClass = getEnclosingClassOfLambda(event.command.javaClass)
        if (isSuperClassOf(enclosingClass, VariableInplaceRenamer::class.java)
            || event.commandName == ActionsBundle.message("action.NextTemplateVariable.text")) {
          apply()
        }
      }

      override fun commandStarted(event: CommandEvent) {
        executeIfRequiredEvent(event) {
          preparingSpan = PerformanceTestSpan.TRACER.spanBuilder(PREPARE_SPAN_NAME).setParent(PerformanceTestSpan.getContext()).startSpan()
        }
      }
    })
    WriteAction.runAndWait<Throwable> {
      val templateState = TemplateManagerImpl.getTemplateState(FileEditorManager.getInstance(context.project).selectedTextEditor!!)
      templateState?.nextTab()
    }
    return actionCallback.toPromise()
  }

  private fun isSuperClassOf(haystack: Class<*>?, needle: Class<*>): Boolean {
    if (haystack == null) return false
    var currentSuperClass: Class<*> = haystack
    while (currentSuperClass !== Object::class.java) {
      if (needle === currentSuperClass) {
        return true
      }
      currentSuperClass = currentSuperClass.superclass
    }
    return false
  }

  private fun getEnclosingClassOfLambda(lambda: Class<Runnable>): Class<*>? {
    val commandClass = lambda.name
    val lambdaOffset = commandClass.indexOf("\$\$Lambda\$")
    if (lambdaOffset > 0) {
      return Class.forName(commandClass.substring(0, lambdaOffset))
    }
    return null
  }
}

