package com.jetbrains.extensions

import com.intellij.openapi.util.NlsSafe

/**
 * [Result] failure with user-readable error
 */
fun <T> failure(error: @NlsSafe String): Result<T> = Result.failure(Throwable(error))