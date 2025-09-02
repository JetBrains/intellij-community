// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.find.FindModel
import com.intellij.find.impl.FindUIHelper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls


/**
 * Runs Find in Files and measures 1) how fast the dialog with an empty query was opened 2) how long search for the given query took
 * Syntax: %findInFiles query1;query2;query9
 * Example: %findInFiles foo
 * Example: %findInFiles foo;bar
 */
class FindInFilesCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "findInFiles"
    const val DIALOG_OPEN_SPAN_NAME: @NonNls String = "findInFiles#openDialog"
    const val SEARCH_SPAN_NAME: @NonNls String = "findInFiles#search"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val queriesString = extractCommandArgument(PREFIX)
    val queries: List<String> = queriesString.split(";")

    val project = context.getProject()

    val findModelEmpty = FindModel().apply {
      isProjectScope = true
    }

    val openDialogMutex = Mutex(true)

    val openDialogSpan = PerformanceTestSpan.TRACER.spanBuilder(DIALOG_OPEN_SPAN_NAME).startSpan()
    val openDialogHelper = withContext(Dispatchers.EDT) {
      FindUIHelper(project, findModelEmpty) {}
    }

    withContext(Dispatchers.EDT) {
      openDialogHelper.showUI()
      openDialogSpan.end()
      openDialogHelper.closeUI()
      openDialogMutex.unlock()
    }

    openDialogMutex.lock()

    queries.forEach {
      val findModel = FindModel().apply {
        stringToFind = it
        isProjectScope = true
      }

      val findHelper = withContext(Dispatchers.EDT) {
        FindUIHelper(project, findModel) {}
      }

      val findSpan = PerformanceTestSpan.TRACER.spanBuilder("$SEARCH_SPAN_NAME: $it").startSpan()
      val findMutex = Mutex(true)

      findHelper.setStopHandler {
        findSpan.end()
        findHelper.closeUI()
        findMutex.unlock()
      }

      withContext(Dispatchers.EDT) {
        findHelper.showUI()
      }

      findMutex.lock()
    }
  }
}
