// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ShCommandTitleUtil {
  private const val MAX_TITLE_LENGTH = 20

  @JvmStatic
  fun getTitle(command: String): @NlsSafe String {
    for (line in command.lineSequence()) {
      val trimmedLine = line.trim()
      if (trimmedLine.isEmpty()) continue

      val title = StringBuilder()
      var truncated = false
      for (word in trimmedLine.split(Regex("\\s+"))) {
        val separatorLength = if (title.isEmpty()) 0 else 1
        if (title.length + separatorLength + word.length > MAX_TITLE_LENGTH) {
          if (title.isEmpty()) {
            title.append(word, 0, MAX_TITLE_LENGTH)
          }
          truncated = true
          break
        }
        if (title.isNotEmpty()) {
          title.append(' ')
        }
        title.append(word)
      }
      return if (truncated) title.append('…').toString() else title.toString()
    }
    return "RunMarkdown"
  }
}
