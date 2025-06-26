// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext

internal class AssertProblemsViewCountCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = "${CMD_PREFIX}assertProblemsViewCount"
    val LOG: Logger = logger<WaitForFinishedCodeAnalysis>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val expectedNumberOfErrors = extractCommandArgument("assertProblemsViewCount").split(" ")[1].toInt()

    ProblemsView
      .getSelectedPanel(context.project)!!

    val editor = (FileEditorManager
      .getInstance(context.project)
      .selectedTextEditor)

    val actualProblems = ProblemsView
      .getSelectedPanel(context.project)!!
      .treeModel.root.getFileProblems(editor!!.virtualFile!!)

    assert(expectedNumberOfErrors == actualProblems.size)
    { "Expected number of errors $expectedNumberOfErrors, actual ${actualProblems.size}\nList of problems:\n$actualProblems" }
  }

  override fun getName(): String = "assertProblemsViewCount"

}