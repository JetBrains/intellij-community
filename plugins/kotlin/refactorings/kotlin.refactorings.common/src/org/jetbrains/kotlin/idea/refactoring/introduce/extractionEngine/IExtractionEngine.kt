// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.event.HyperlinkEvent

abstract class IExtractionEngineHelper<KotlinType,
        ExtractionData : IExtractionData,
        Config : IExtractionGeneratorConfiguration<KotlinType>,
        Result : IExtractionResult<KotlinType>,
        Descriptor : IExtractableCodeDescriptor<KotlinType>,
        DescriptorWithConflicts : IExtractableCodeDescriptorWithConflicts<KotlinType>>(@NlsContexts.DialogTitle val operationName: String) {

    open fun adjustExtractionData(data: ExtractionData): ExtractionData = data

    fun doRefactor(config: Config, onFinish: (Result) -> Unit = {}) {
        val project = config.descriptor.extractionData.project
        var extracted: SmartPsiElementPointer<KtNamedDeclaration>? = null
        val result = project.executeCommand<Result?>(operationName) {
            var generatedDeclaration: Result? = null
            ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(KotlinBundle.message("perform.refactoring"), project, null) {
                generatedDeclaration = generateDeclaration(config)
                extracted = generatedDeclaration?.declaration?.createSmartPointer()
            }
            generatedDeclaration
        } ?: return

        extracted?.element?.let {
            //update declaration after reformat
            result.declaration = it
        }
        onFinish(result)
    }

    /**
     * Prepare extract descriptor
     */
    abstract fun generateDeclaration(config: Config): Result

    /**
     * Search for potential conflicts
     */
    abstract fun validate(descriptor: Descriptor): DescriptorWithConflicts

    /**
     * Shows dialog/start template and starts refactoring
     */
    abstract fun configureAndRun(
        project: Project,
        editor: Editor,
        descriptorWithConflicts: DescriptorWithConflicts,
        onFinish: (Result) -> Unit = {}
    )
}

abstract class IExtractionEngine<KotlinType,
        ExtractionData : IExtractionData,
        Config : IExtractionGeneratorConfiguration<KotlinType>,
        Result : IExtractionResult<KotlinType>,
        Descriptor : IExtractableCodeDescriptor<KotlinType>,
        DescriptorWithConflicts : IExtractableCodeDescriptorWithConflicts<KotlinType>>
    (val helper: IExtractionEngineHelper<KotlinType, ExtractionData, Config, Result, Descriptor, DescriptorWithConflicts>) {
    /**
     * Prepare extraction result
     *
     * @param extractionData The extraction data to be analyzed.
     * @return The analysis result, which includes the descriptor, status, and messages.
     */
    abstract fun performAnalysis(extractionData: ExtractionData): AnalysisResult<KotlinType>

    fun run(
        editor: Editor,
        extractionData: ExtractionData,
        onFinish: (Result) -> Unit = {}
    ) {
        val project = extractionData.project

        val adjustExtractionData = helper.adjustExtractionData(extractionData)
        val analysisResult = ActionUtil.underModalProgress(project, KotlinBundle.message("progress.title.analyze.extraction.data")) {
            performAnalysis(adjustExtractionData)
        }

        if (ApplicationManager.getApplication().isUnitTestMode() && analysisResult.status != AnalysisResult.Status.SUCCESS) {
            throw BaseRefactoringProcessor.ConflictsInTestsException(analysisResult.messages.map { it.renderMessage() })
        }

        fun validateAndRefactor() {
            val callable: () -> Any = {
                try {
                    helper.validate(analysisResult.descriptor as Descriptor)
                } catch (e: RuntimeException) {
                    if (e is ControlFlowException) {
                        throw e
                    }
                    ExtractableCodeDescriptorWithException(e)
                }
            }
            val finishOnUIThread: (Any) -> Unit = { result ->
                (result as? ExtractableCodeDescriptorWithException)?.let { throw it.exception }
                val validationResult = result as DescriptorWithConflicts
                project.checkConflictsInteractively(validationResult.conflicts) {
                    helper.configureAndRun(project, editor, validationResult) {
                        try {
                            onFinish(it)
                        } finally {
                            it.dispose()
                            extractionData.dispose()
                        }
                    }
                }
            }
            val result = ActionUtil.underModalProgress(project, KotlinBundle.message("progress.title.check.for.conflicts"), callable)
            if (result != null) {
                finishOnUIThread(result)
            }
        }

        val message = analysisResult.messages.joinToString("\n") { it.renderMessage() }
        when (analysisResult.status) {
            AnalysisResult.Status.CRITICAL_ERROR -> {
                showErrorHint(project, editor, message, helper.operationName)
            }

            AnalysisResult.Status.NON_CRITICAL_ERROR -> {
                val anchorPoint = RelativePoint(
                    editor.contentComponent,
                    editor.visualPositionToXY(editor.selectionModel.selectionStartPosition!!)
                )
                @NlsSafe val htmlContent =
                    "$message<br/><br/><a href=\"EXTRACT\">${KotlinBundle.message("text.proceed.with.extraction")}</a>"
                JBPopupFactory.getInstance()!!
                    .createHtmlTextBalloonBuilder(
                        htmlContent,
                        MessageType.WARNING
                    ) { event ->
                        if (event?.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                            validateAndRefactor()
                        }
                    }
                    .setHideOnClickOutside(true)
                    .setHideOnFrameResize(false)
                    .setHideOnLinkClick(true)
                    .createBalloon()
                    .show(anchorPoint, Balloon.Position.below)
            }

            AnalysisResult.Status.SUCCESS -> validateAndRefactor()
        }
    }
}
