// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

/**
 * @param completionType - type of completion items to choose post-processing function in the future
 */
data class CompletionResultData(
  val setOfCompletionItems: Set<String>,
  val completionType: PyRuntimeCompletionType,
  val referenceString: String,
)