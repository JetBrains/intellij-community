package com.intellij.performanceTesting.freezes.diogen

/**
 * Generates titles from freeze reports (thread dumps).
 */
object FreezeTitleGenerator {
  fun selectCallable(cause: ThreadDumpParser.Trace): String? {
    val preprocessedCalls = cause.lines.map { it.toString().removePrefix("\tat ") }.map {
      val indexOfParenthesis = it.indexOf('(')
      if (indexOfParenthesis >= 0) {
        it.substring(0, indexOfParenthesis)
      }
      else {
        it
      }
    }.filter { it.trim().isNotEmpty() }

    fun String.isFilteredFrame() = FrameFilters.FILTERED_FRAMES.any { frame -> this.contains(frame) }

    val indexOfReadActionInSmart = preprocessedCalls.indexOfFirst { line ->
      line.startsWith("com.intellij.openapi.project.DumbService.runReadActionInSmartMode")
    }
    if (indexOfReadActionInSmart > 0) {
      for (i in indexOfReadActionInSmart - 1 downTo 0) {
        val candidate = preprocessedCalls[i]
        if (!candidate.isFilteredFrame()) {
          return candidate
        }
      }
    }

    var blockingReadAction: String? = null
    preprocessedCalls.forEachIndexed { i, line ->
      if (line == "com.intellij.openapi.application.impl.ApplicationImpl.runReadAction" ||
          line == "com.intellij.openapi.application.ActionsKt.runReadAction" ||
          line == "com.intellij.openapi.application.ReadAction.compute" ||
          line == "com.intellij.openapi.application.ReadAction.run"
      ) {
        for (j in i + 1 until preprocessedCalls.size) {
          val candidate = preprocessedCalls[j]
          if (!candidate.isFilteredFrame()) {
            blockingReadAction = candidate
            break
          }
        }

        if (blockingReadAction != null) return@forEachIndexed
      }
    }
    if (blockingReadAction != null) return blockingReadAction

    return preprocessedCalls.firstOrNull { line -> !line.isFilteredFrame() }
           ?: preprocessedCalls.firstOrNull()
  }

  fun formatTitle(callable: String?): String? {
    return callable?.substringBefore("\$Lambda")?.plus("\$Lambda")
  }

}
