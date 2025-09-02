package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.util.TruncateMode

object Constants {
  const val RELATIVE_PATH_IN_PROJECT_DESCRIPTION: String = "Path relative to the project root"
  const val TIMEOUT_MILLISECONDS_DESCRIPTION: String = "Timeout in milliseconds"
  const val LONG_TIMEOUT_MILLISECONDS_VALUE: Int = 60 * 1000
  const val MEDIUM_TIMEOUT_MILLISECONDS_VALUE: Int = 10 * 1000
  const val SHORT_TIMEOUT_MILLISECONDS_VALUE: Int = 1 * 1000
  const val MAX_LINES_COUNT_DESCRIPTION: String = "Maximum number of lines to return"
  const val MAX_LINES_COUNT_VALUE: Int = 1000
  const val TRUNCATE_MODE_DESCRIPTION: String = "How to truncate the text: from the start, in the middle, at the end, or don't truncate at all"
  val TRUCATE_MODE_VALUE: TruncateMode = TruncateMode.START
}