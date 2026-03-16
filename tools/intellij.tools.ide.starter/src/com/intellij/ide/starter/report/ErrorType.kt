package com.intellij.ide.starter.report

enum class ErrorType{
  ERROR, FREEZE, TIMEOUT;

  companion object {
    fun fromMessage(message: String): ErrorType =
      if (message.startsWith("UI was frozen") || message.startsWith("Freeze in EDT")) FREEZE
      else if (message.startsWith("Timeout of IDE run")) TIMEOUT
      else ERROR
  }
}