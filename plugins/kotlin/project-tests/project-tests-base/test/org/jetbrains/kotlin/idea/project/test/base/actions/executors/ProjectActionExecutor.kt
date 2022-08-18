// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.actions.executors

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.project.test.base.actions.ActionExecutionResultError
import org.jetbrains.kotlin.idea.project.test.base.metrics.MetricsCollector
import org.jetbrains.kotlin.idea.project.test.base.metrics.MetricsData
import org.jetbrains.kotlin.idea.performance.tests.utils.commitAllDocuments

abstract class ProjectActionExecutor<R : Any, CONTEXT> {

    protected abstract fun performAction(context: CONTEXT, collector: MetricsCollector, data: ProjectActionExecutorData): R?

    protected abstract fun checkResultForValidity(result: R, context: CONTEXT): ActionExecutionResultError.InvalidActionExecutionResult?

    protected abstract fun setUp(data: ProjectActionExecutorData): CONTEXT

    protected abstract fun tearDown(context: CONTEXT, data: ProjectActionExecutorData)


    fun execute(data: ProjectActionExecutorData): MetricsData {
        val context = setUp(data)
        prepare(data)
        return performActionAndGetMetricsData(context, data)
    }

    private fun prepare(data: ProjectActionExecutorData) {
        commitAllDocuments()
        runWriteAction {
            invalidateBaseCaches(data)
            data.profile.frontendConfiguration.invalidateCaches(data.project)
        }
        System.gc()
    }

    private fun performActionAndGetMetricsData(context: CONTEXT, data: ProjectActionExecutorData): MetricsData {
        val collector = MetricsCollector(data.iteration)
        try {
            val result = performAction(context, collector, data)
            validateResult(result, context, collector, data)
        } finally {
            tearDown(context, data)
        }
        return collector.getMetricsData()
    }

    private fun invalidateBaseCaches(data: ProjectActionExecutorData) {
        PsiManager.getInstance(data.project).apply {
            dropResolveCaches()
            dropPsiCaches()
        }
    }

    private fun validateResult(
        result: R?,
        context: CONTEXT,
        collector: MetricsCollector,
        data: ProjectActionExecutorData,
    ) {
        if (result != null && data.profile.checkForValidity) {
            val validationResult = checkResultForValidity(result, context)
            if (validationResult != null) {
                collector.reportFailure(validationResult)
            }
        }
    }

}