// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindUIHelper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageViewPresentation
import com.jetbrains.performancePlugin.PerformanceTestSpan
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls


/**
 * Runs Find in Files and measures 1) how fast the dialog with an empty query was opened 2) how long search for the given query took
 * Syntax: %findInFiles [-headless] [-directory=<path>] query1;query2;query9
 * Example: %findInFiles foo
 * Example: %findInFiles foo;bar
 * Example: %findInFiles -headless -directory=/work/project/platform foo;bar
 *
 * Without `-directory`, each search takes the project scope, and its span is [SEARCH_SPAN_NAME]. With it, each search
 * takes the directory scope of the absolute `<path>` and its subdirectories, and its span is [DIRECTORY_SEARCH_SPAN_NAME].
 * The path holds no space.
 *
 * With `-headless`, the command opens no popup: each search runs [FindInProjectUtil.findUsages] with the model to its
 * last usage, and the dialog span is left out. A product whose popup search does not finish in a test run, such as
 * JetBrains Light, which holds the dumb mode, measures the search engines this way.
 */
class FindInFilesCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "findInFiles"
    const val DIALOG_OPEN_SPAN_NAME: @NonNls String = "findInFiles#openDialog"
    const val SEARCH_SPAN_NAME: @NonNls String = "findInFiles#search"
    const val DIRECTORY_SEARCH_SPAN_NAME: @NonNls String = "findInFiles#searchInDirectory"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (directory, headless, queries) = parseFindInFilesArgument(extractCommandArgument(PREFIX))

    val project = context.getProject()
    if (headless) {
      for (query in queries) {
        searchWithoutPopup(project, findModel(query, directory), spanName(directory, query))
      }
      return
    }

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
      val findModel = findModel(it, directory)

      val findHelper = withContext(Dispatchers.EDT) {
        FindUIHelper(project, findModel) {}
      }

      val findSpan = PerformanceTestSpan.TRACER.spanBuilder(spanName(directory, it)).startSpan()
      val findMutex = Mutex(true)

      findHelper.setFinishHandler { resultsCount ->
        findSpan.setAttribute("resultsCount", resultsCount.toLong())
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

private const val DIRECTORY_OPTION: @NonNls String = "-directory="
private const val HEADLESS_OPTION: @NonNls String = "-headless"

/** The span of one search: the query, in the scope of the project or of [directory]. */
private fun spanName(directory: String?, query: String): String =
  "${if (directory == null) FindInFilesCommand.SEARCH_SPAN_NAME else FindInFilesCommand.DIRECTORY_SEARCH_SPAN_NAME}: $query"

/** The model of one search of [query]: the project scope, or the scope of [directory] with its subdirectories. */
private fun findModel(query: String, directory: String?): FindModel = FindModel().apply {
  stringToFind = query
  if (directory == null) {
    isProjectScope = true
  }
  else {
    isProjectScope = false
    directoryName = directory
    isWithSubdirectories = true
  }
}

/** Runs [model] to its last usage without a popup, under the span [spanName] with the usage count as `resultsCount`. */
private suspend fun searchWithoutPopup(project: Project, model: FindModel, spanName: String) {
  val span = PerformanceTestSpan.TRACER.spanBuilder(spanName).startSpan()
  val usages = AtomicInteger()
  try {
    withContext(Dispatchers.Default) {
      coroutineToIndicator { _ ->
        FindInProjectUtil.findUsages(model, project, { usages.incrementAndGet(); true }, FindUsagesProcessPresentation(UsageViewPresentation()))
      }
    }
  }
  finally {
    span.setAttribute("resultsCount", usages.get().toLong())
    span.end()
  }
}

/**
 * The argument of [FindInFilesCommand]: the directory of `-directory=<path>`, or null for the project scope, whether
 * `-headless` asks for a search without the popup, and the queries.
 */
internal data class FindInFilesArgument(val directory: String?, val headless: Boolean, val queries: List<String>)

/** Reads the options before the queries: each is a word that starts with `-`, and the first other word starts the queries. */
internal fun parseFindInFilesArgument(argument: String): FindInFilesArgument {
  var rest = argument
  var directory: String? = null
  var headless = false
  while (true) {
    val word = rest.substringBefore(' ')
    when {
      word == HEADLESS_OPTION -> headless = true
      word.startsWith(DIRECTORY_OPTION) -> directory = word.removePrefix(DIRECTORY_OPTION)
      else -> return FindInFilesArgument(directory, headless, rest.split(";"))
    }
    rest = rest.substringAfter(' ', "")
  }
}
