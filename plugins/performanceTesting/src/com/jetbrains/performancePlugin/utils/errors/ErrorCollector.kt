package com.jetbrains.performancePlugin.utils.errors

interface ErrorCollector {
  fun addError(error: Throwable)

  fun <T> runCatchingError(computation: () -> T): T?

  val numberOfErrors: Int
}