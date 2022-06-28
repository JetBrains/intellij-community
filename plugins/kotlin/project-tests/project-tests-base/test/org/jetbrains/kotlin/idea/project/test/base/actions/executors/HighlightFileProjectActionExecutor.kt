// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.actions.executors

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.configureInspections
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.ArrayUtilRt
import org.jetbrains.kotlin.idea.project.test.base.actions.ActionExecutionResultError
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction
import org.jetbrains.kotlin.idea.project.test.base.actions.reportMetricForAction
import org.jetbrains.kotlin.idea.project.test.base.metrics.DefaultMetrics
import org.jetbrains.kotlin.idea.project.test.base.metrics.MetricsCollector
import org.jetbrains.kotlin.idea.performance.tests.utils.commitAllDocuments
import org.jetbrains.kotlin.idea.performance.tests.utils.project.openInEditor
import org.jetbrains.kotlin.psi.KtFile

object HighlightFileProjectActionExecutor : ProjectActionExecutor<List<HighlightInfo>, HighlightFileProjectActionExecutor.Context>() {
    override fun performAction(context: Context, collector: MetricsCollector, data: ProjectActionExecutorData): List<HighlightInfo>? {
        return collector.reportMetricForAction(DefaultMetrics.valueMetric) {
            CodeInsightTestFixtureImpl.instantiateAndRun(context.ktFile, context.editor, ArrayUtilRt.EMPTY_INT_ARRAY, true)
        }
    }

    override fun checkResultForValidity(result: List<HighlightInfo>, context: Context): ActionExecutionResultError.InvalidActionExecutionResult? {
        val filename = context.ktFile.name
        val errors = result.filter { it.severity == HighlightSeverity.ERROR }
        return if (errors.isNotEmpty()) {
            ActionExecutionResultError.InvalidActionExecutionResult(
                "The following errors arose during highlighting of $filename:\n${errors.joinToString(separator = "\n")}"
            )
        } else null
    }

    override fun setUp(data: ProjectActionExecutorData): Context {
        val ktFile = openFile(data.project, data.action)
        val editor = getEditor(ktFile)
        val currentProfile = setupEmptyInspectionProfileAndGetOldProfile(data.project)

        commitAllDocuments()

        return Context(ktFile, editor, currentProfile)
    }

    private fun setupEmptyInspectionProfileAndGetOldProfile(project: Project): InspectionProfileImpl {
        val profileManager = ProjectInspectionProfileManager.getInstance(project)
        val currentProfile = profileManager.currentProfile
        configureInspections(emptyArray(), project, project)
        return currentProfile
    }

    private fun getEditor(ktFile: KtFile): Editor {
        val document = FileDocumentManager.getInstance().getDocument(ktFile.virtualFile)!!
        return EditorFactory.getInstance().getEditors(document).single()
    }

    private fun openFile(project: Project, action: ProjectAction): KtFile =
        openInEditor(project, action.filePath).psiFile as KtFile

    override fun tearDown(context: Context, data: ProjectActionExecutorData) {
        FileEditorManager.getInstance(data.project).closeFile(context.ktFile.virtualFile)
        ProjectInspectionProfileManager.getInstance(data.project).setCurrentProfile(context.inspectionProfile)
    }

    data class Context(
        val ktFile: KtFile,
        val editor: Editor,
        val inspectionProfile: InspectionProfileImpl,
    )
}