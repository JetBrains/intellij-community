package com.jetbrains.python

import com.intellij.openapi.util.NlsSafe
import kotlin.Result

/**
 * [kotlin.Result] failure with user-readable error
 */
fun <T> failure(error: @NlsSafe String): Result<T> = Result.Companion.failure(Throwable(error))