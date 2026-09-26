// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils.errors

interface ErrorCollector {
  fun addError(error: Throwable)

  fun <T> runCatchingError(computation: () -> T): T?

  val numberOfErrors: Int
}