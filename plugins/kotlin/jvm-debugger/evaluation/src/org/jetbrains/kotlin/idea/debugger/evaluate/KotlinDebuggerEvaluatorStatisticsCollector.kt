// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats

object KotlinDebuggerEvaluatorStatisticsCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup("kotlin.debugger.evaluator", 5)

    // fields
    private val compilerField = EventFields.Enum<CompilerType>("compiler")
    private val resultField = EventFields.Enum<StatisticsEvaluationResult>("result")
    private val wrapTimeMsField = EventFields.Long("wrap_time_ms")
    private val analysisTimeMsField = EventFields.Long("analysis_time_ms")
    private val compilationTimeMsField = EventFields.Long("compilation_time_ms")
    private val wholeTimeField = EventFields.Long("whole_time_field")
    private val interruptionsField = EventFields.Int("total_interruptions")

    // events
    private val analysisCompilationEvent = GROUP.registerVarargEvent(
        "analysis.compilation.result", compilerField, resultField,
        wrapTimeMsField, analysisTimeMsField, compilationTimeMsField, wholeTimeField, interruptionsField
    )
    // no need to record evaluation time, as it reflects what user evaluates, not how effective our evaluation is
    private val evaluationEvent = GROUP.registerEvent("evaluation.result", resultField, compilerField)

    @JvmStatic
    fun logAnalysisAndCompilationResult(
        project: Project?, compilerType: CompilerType, evaluationResult: StatisticsEvaluationResult,
        stats: CodeFragmentCompilationStats
    ) {
        analysisCompilationEvent.log(
            project,
            EventPair(compilerField, compilerType),
            EventPair(resultField, evaluationResult),
            EventPair(wrapTimeMsField, stats.wrapTimeMs),
            EventPair(analysisTimeMsField, stats.analysisTimeMs),
            EventPair(compilationTimeMsField, stats.compilationTimeMs),
            EventPair(wholeTimeField, System.currentTimeMillis() - stats.startTimeMs),
            EventPair(interruptionsField, stats.interruptions)
        )
    }

    @JvmStatic
    internal fun logEvaluationResult(project: Project?, evaluationResult: StatisticsEvaluationResult, compilerType: CompilerType) {
        evaluationEvent.log(project, evaluationResult, compilerType)
    }
}

enum class StatisticsEvaluationResult {
    SUCCESS, FAILURE
}
