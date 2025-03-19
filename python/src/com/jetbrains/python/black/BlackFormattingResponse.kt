// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

sealed class BlackFormattingResponse {
  class Success(val formattedText: String) : BlackFormattingResponse()

  class Ignored(@Nls val title: String,
                @NlsSafe val description: String) : BlackFormattingResponse()

  class Failure(@Nls val title: String,
                @NlsSafe val description: String,
                val exitCode: Int?) : BlackFormattingResponse() {

    fun getLoggingMessage(): String {
      val stringBuilder = StringBuilder()
      stringBuilder.append("${title}\n")
      if (description.isNotEmpty()) {
        stringBuilder.append("stderr: ${description}\n")
      }
      if (exitCode != null) {
        stringBuilder.append("exit code: ${exitCode}\n")
      }
      return stringBuilder.toString()
    }

    @NlsSafe
    fun getInlineNotificationMessage() = trimBlackErrorMessage(64)

    @NlsSafe
    fun getPopupMessage(): String = trimBlackErrorMessage(256)

    private fun trimBlackErrorMessage(maxLength: Int): String {
      val errorInfo = this.description
                        .lines()
                        .firstOrNull()
                        ?.replaceBefore("Cannot parse", "")
                      ?: this.description
      return if (errorInfo.length > maxLength) errorInfo.substring(0, maxLength) + "..." else errorInfo
    }
  }
}