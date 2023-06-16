// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class KotlinDebuggerEvaluatorStatisticsCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        private val GROUP = EventLogGroup("kotlin.debugger.evaluator", 1)

        // fields
        private val evaluatorField = EventFields.Enum<StatisticsEvaluator>("evaluator")
        private val evaluationResultField = EventFields.Enum<StatisticsEvaluationResult>("evaluation_result")

        // events
        private val evaluationResultEvent = GROUP.registerEvent("evaluation.result", evaluatorField, evaluationResultField)
        private val fallbackToOldEvaluatorEvent = GROUP.registerEvent("fallback.to.old.evaluator")

        @JvmStatic
        fun logEvaluationResult(project: Project?, evaluator: StatisticsEvaluator, evaluationResult: StatisticsEvaluationResult) {
            evaluationResultEvent.log(project, evaluator, evaluationResult)
        }

        @JvmStatic
        fun logFallbackToOldEvaluator(project: Project?) {
            fallbackToOldEvaluatorEvent.log(project)
        }
    }
}

enum class StatisticsEvaluator {
    OLD, IR //, K2
}

enum class StatisticsEvaluationResult {
    SUCCESS, FAILURE
}
