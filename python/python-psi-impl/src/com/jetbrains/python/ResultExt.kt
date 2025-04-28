package com.jetbrains.python

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import kotlin.Result

/**
 * Please use [com.jetbrains.python.Result] (low level) or ([com.jetbrains.python.errorProcessing.PyResult] high level)
 * [kotlin.Result] failure with user-readable error
 */
@ApiStatus.Obsolete
fun <T> failure(error: @NlsSafe String): Result<T> = Result.Companion.failure(Throwable(error))