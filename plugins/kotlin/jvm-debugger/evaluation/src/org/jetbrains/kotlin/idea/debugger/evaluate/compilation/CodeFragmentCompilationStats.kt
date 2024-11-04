// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable

@ApiStatus.Internal
class CodeFragmentCompilationStats {
    val startTimeMs: Long = System.currentTimeMillis()

    var wrapTimeMs: Long = -1L
        private set
    var analysisTimeMs: Long = -1L
        private set
    var compilationTimeMs: Long = -1L
        private set
    var interruptions: Int = 0
        private set

    var origin: XEvaluationOrigin = XEvaluationOrigin.UNSPECIFIED

    var compilerFailExceptionClass: Class<out Throwable>? = null

    fun <R> startAndMeasureWrapAnalysisUnderReadAction(block: () -> R): Result<R> = startAndMeasureUnderReadAction(block) { wrapTimeMs = it }
    fun <R> startAndMeasureAnalysisUnderReadAction(block: () -> R): Result<R> = startAndMeasureUnderReadAction(block) { analysisTimeMs = it }
    fun <R> startAndMeasureCompilationUnderReadAction(block: () -> R): Result<R> = startAndMeasureUnderReadAction(block) { compilationTimeMs = it }

    private fun <R> startAndMeasureUnderReadAction(block: () -> R, timeUpdater: (Long) -> Unit): Result<R> {
        return try {
            val startMs = System.currentTimeMillis()
            val result = ReadAction.nonBlocking(Callable {
                try {
                    block()
                } catch (e: ProcessCanceledException) {
                    interruptions++
                    throw e
                }
            }).executeSynchronously()
            timeUpdater(System.currentTimeMillis() - startMs)
            Result.success(result)
        }
        catch (e: ProcessCanceledException) {
            throw e
        }
        catch (e: Exception) {
            Result.failure(e)
        }
    }
}