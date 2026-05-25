// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

sealed class BlackFormattingResponse {
  class Success(val formattedText: String) : BlackFormattingResponse()

  class Ignored(val title: @Nls String,
                val description: @NlsSafe String) : BlackFormattingResponse()

  class Failure(val title: @Nls String,
                val description: @NlsSafe String,
                val exitCode: Int?) : BlackFormattingResponse() {

    fun getInlineNotificationMessage(): @NlsSafe String = trimBlackErrorMessage()

    private fun trimBlackErrorMessage(maxLength: Int = 64): String {
      val errorInfo = this.description
                        .lines()
                        .firstOrNull()
                        ?.replaceBefore("Cannot parse", "")
                      ?: this.description
      return if (errorInfo.length > maxLength) errorInfo.substring(0, maxLength) + "..." else errorInfo
    }
  }
}