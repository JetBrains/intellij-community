// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions.internal

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import kotlin.system.measureTimeMillis

private const val ITERATIONS = 10

internal class PyTypeBenchmarkAction : AnAction() {
  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val document = editor.document
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) as? PyFile ?: return

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Benchmarking Python type evaluation", false) {
      override fun run(indicator: ProgressIndicator) {
        val results = mutableListOf<String>()
        val marker = "\n# bench"

        for (i in 1..ITERATIONS) {
          indicator.text = "Iteration $i of $ITERATIONS"
          indicator.fraction = (i - 1).toDouble() / ITERATIONS

          var elementCount = 0
          var elapsedMs = 0L
          ProgressManager.getInstance().executeNonCancelableSection {
            ApplicationManager.getApplication().runReadAction {
              val context = TypeEvalContext.userInitiated(project, psiFile)
              val elements = PsiTreeUtil.collectElementsOfType(psiFile, PyTypedElement::class.java)
              elementCount = elements.size
              elapsedMs = measureTimeMillis {
                for (element in elements) {
                  context.getType(element)
                }
              }
            }
          }

          results.add("Iteration $i: $elementCount elements, $elapsedMs ms")

          // Invalidate PSI/type caches by making a document edit and reverting it
          ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project, psiFile).withName("Benchmark Cache Invalidation").run<RuntimeException> {
              document.insertString(document.textLength, marker)
              PsiDocumentManager.getInstance(project).commitDocument(document)
            }
            WriteCommandAction.writeCommandAction(project, psiFile).withName("Benchmark Cache Invalidation").run<RuntimeException> {
              val text = document.text
              val idx = text.lastIndexOf(marker)
              if (idx >= 0) {
                document.deleteString(idx, idx + marker.length)
                PsiDocumentManager.getInstance(project).commitDocument(document)
              }
            }
          }
        }

        indicator.fraction = 1.0
        val content = results.joinToString("<br>")
        Notifications.Bus.notify(
          Notification("Python.Internal", "Python type evaluation benchmark", content, NotificationType.INFORMATION),
          project,
        )
      }
    })
  }

  override fun update(e: AnActionEvent) {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE)
    e.presentation.isEnabledAndVisible = psiFile is PyFile
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
