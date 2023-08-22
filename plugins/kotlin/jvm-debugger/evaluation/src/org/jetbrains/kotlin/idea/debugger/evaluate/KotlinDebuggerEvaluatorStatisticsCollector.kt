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

    private val GROUP = EventLogGroup("kotlin.debugger.evaluator", 2)

    // fields
    private val evaluatorField = EventFields.Enum<StatisticsEvaluator>("evaluator")
    private val evaluationResultField = EventFields.Enum<StatisticsEvaluationResult>("evaluation_result")
    private val analysisTimeMsField = EventFields.Long("analysis_time_ms")
    private val compilationTimeMsField = EventFields.Long("compilation_time_ms")
    private val interruptionsField = EventFields.Int("total_interruptions")

    // events
    private val evaluationResultEvent = GROUP.registerVarargEvent(
        "evaluation.result", evaluatorField, evaluationResultField,
        analysisTimeMsField, compilationTimeMsField, interruptionsField
    )
    private val fallbackToOldEvaluatorEvent = GROUP.registerEvent("fallback.to.old.evaluator")

    @JvmStatic
    fun logEvaluationResult(
        project: Project?, evaluator: StatisticsEvaluator, evaluationResult: StatisticsEvaluationResult,
        stats: CodeFragmentCompilationStats
    ) {
        evaluationResultEvent.log(
            project,
            EventPair(evaluatorField, evaluator),
            EventPair(evaluationResultField, evaluationResult),
            EventPair(analysisTimeMsField, stats.analysisTimeMs),
            EventPair(compilationTimeMsField, stats.compilationTimeMs),
            EventPair(interruptionsField, stats.interruptions)
        )
    }

    @JvmStatic
    fun logFallbackToOldEvaluator(project: Project?) {
        fallbackToOldEvaluatorEvent.log(project)
    }
}

enum class StatisticsEvaluator {
    OLD, IR //, K2
}

enum class StatisticsEvaluationResult {
    SUCCESS, FAILURE
}
