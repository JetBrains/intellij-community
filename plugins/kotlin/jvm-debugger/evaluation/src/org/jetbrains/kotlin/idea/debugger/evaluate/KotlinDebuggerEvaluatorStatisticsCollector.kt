// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats
import java.util.concurrent.ExecutionException

internal val EvaluationContextImpl.origin: XEvaluationOrigin get() = XEvaluationOrigin.getOrigin(this)

object KotlinDebuggerEvaluatorStatisticsCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup("kotlin.debugger.evaluator", 9)

    // fields
    private val compilerField = EventFields.Enum<CompilerType>("compiler")
    private val originField = EventFields.Enum<XEvaluationOrigin>("origin")
    private val compilationResultField = EventFields.Enum<EvaluationCompilerResult>("compilation_result")
    private val resultField = EventFields.Enum<StatisticsEvaluationResult>("result")
    private val wrapTimeMsField = EventFields.Long("wrap_time_ms")
    private val analysisTimeMsField = EventFields.Long("analysis_time_ms")
    private val compilationTimeMsField = EventFields.Long("compilation_time_ms")
    private val wholeTimeField = EventFields.Long("whole_time_field")
    private val interruptionsField = EventFields.Int("total_interruptions")
    private val compilerExceptionField = EventFields.Class("exception")

    // events
    private val analysisCompilationEvent = GROUP.registerVarargEvent(
        "analysis.compilation.result", compilerField, originField, compilationResultField,
        wrapTimeMsField, analysisTimeMsField, compilationTimeMsField, wholeTimeField, interruptionsField, compilerExceptionField
    )
    // no need to record evaluation time, as it reflects what user evaluates, not how effective our evaluation is
    private val evaluationEvent = GROUP.registerEvent("evaluation.result", resultField, compilerField, originField)

    @JvmStatic
    fun logAnalysisAndCompilationResult(
        project: Project?, compilerType: CompilerType, compilationResult: EvaluationCompilerResult,
        stats: CodeFragmentCompilationStats
    ) {
        val parameters = listOf(
            EventPair(compilerField, compilerType),
            EventPair(originField, stats.origin),
            EventPair(compilationResultField, compilationResult),
            EventPair(wrapTimeMsField, stats.wrapTimeMs),
            EventPair(analysisTimeMsField, stats.analysisTimeMs),
            EventPair(compilationTimeMsField, stats.compilationTimeMs),
            EventPair(wholeTimeField, System.currentTimeMillis() - stats.startTimeMs),
            EventPair(interruptionsField, stats.interruptions),
        ) + (stats.compilerFailExceptionClass?.let { listOf(EventPair(compilerExceptionField, it)) } ?: emptyList())
        analysisCompilationEvent.log(project, parameters)
    }

    @JvmStatic
    internal fun logEvaluationResult(project: Project?, evaluationResult: StatisticsEvaluationResult, compilerType: CompilerType, origin: XEvaluationOrigin) {
        evaluationEvent.log(project, evaluationResult, compilerType, origin)
    }
}

enum class EvaluationCompilerResult {
    SUCCESS, COMPILATION_FAILURE, COMPILER_INTERNAL_ERROR
}

enum class StatisticsEvaluationResult {
    SUCCESS,
    USER_EXCEPTION,

    COMPILATION_FAILURE,
    COMPILER_INTERNAL_ERROR,
    UNCLASSIFIED_COMPILATION_PROBLEM,

    UNCLASSIFIED_EVALUATION_PROBLEM,
    MISCOMPILED,
    ERROR_DURING_PARSING_EXCEPTION,
    WRONG_JVM_STATE,
    UNRELATED_EXCEPTION,
}

@ApiStatus.Internal
fun extractExceptionCauseClass(e: Throwable): Class<out Throwable> {
    val cause = (e as? ExecutionException)?.cause ?: e

    return if (cause is EvaluateException) {
        cause.cause?.javaClass ?: cause.javaClass
    } else {
        cause.javaClass
    }
}
