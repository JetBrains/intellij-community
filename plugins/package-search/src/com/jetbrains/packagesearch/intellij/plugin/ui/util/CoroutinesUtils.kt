@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.jetbrains.packagesearch.intellij.plugin.ui.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

// This file is meant to centralize the OptIn declarations so that when they will be stabilized
// a deprecation cycle would be enough to remove them

/**
 * Returns a flow that mirrors the original flow, but filters out values
 * that are followed by the newer values within the given [timeout][timeoutMillis].
 * The latest value is always emitted.
 *
 * Example:
 *
 * ```kotlin
 * flow {
 *     emit(1)
 *     delay(90)
 *     emit(2)
 *     delay(90)
 *     emit(3)
 *     delay(1010)
 *     emit(4)
 *     delay(1010)
 *     emit(5)
 * }.debounce(1000)
 * ```
 * <!--- KNIT example-delay-01.kt -->
 *
 * produces the following emissions
 *
 * ```text
 * 3, 4, 5
 * ```
 * <!--- TEST -->
 *
 * Note that the resulting flow does not emit anything as long as the original flow emits
 * items faster than every [timeoutMillis] milliseconds.
 */
internal fun <T> Flow<T>.debounce(timeoutMillis: Long) =
    debounce(timeoutMillis)
