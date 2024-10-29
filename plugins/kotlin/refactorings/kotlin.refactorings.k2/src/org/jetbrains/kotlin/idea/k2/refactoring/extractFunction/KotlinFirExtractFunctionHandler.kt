// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ui.KotlinFirExtractFunctionDialog
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionDataAnalyzer
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractInplaceExtractionHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION_SHOULD_COLLAPSE_BODY
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionEngine
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicates
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinFirExtractFunctionHandler(
    private val acceptAllScopes: Boolean = false,
    private val helper: ExtractionEngineHelper = InplaceExtractionHelper(acceptAllScopes)
) :
    AbstractExtractKotlinFunctionHandler(acceptAllScopes, true) {

    object InteractiveExtractionHelper : ExtractionEngineHelper(EXTRACT_FUNCTION) {
        @OptIn(KaAllowAnalysisOnEdt::class)
        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            fun afterFinish(extraction: ExtractionResult) {
                processDuplicates(extraction.duplicateReplacers, project, editor)
                onFinish(extraction)
            }
            allowAnalysisOnEdt {
                KotlinFirExtractFunctionDialog(descriptorWithConflicts.descriptor.extractionData.project, descriptorWithConflicts) {
                    doRefactor(ExtractionGeneratorConfiguration(it, withCollapseOption(project)), ::afterFinish)
                }.show()
            }
        }
    }

    class InplaceExtractionHelper(private val acceptAllScopes: Boolean) : ExtractionEngineHelper(EXTRACT_FUNCTION),
                                                                          AbstractInplaceExtractionHelper<KaType, ExtractionResult, ExtractableCodeDescriptorWithConflicts> {
        override fun createRestartHandler(): AbstractExtractKotlinFunctionHandler =
            KotlinFirExtractFunctionHandler(acceptAllScopes, InteractiveExtractionHelper)

        override fun createInplaceRestartHandler(): AbstractExtractKotlinFunctionHandler =
            KotlinFirExtractFunctionHandler(acceptAllScopes, this)

        override fun doRefactor(
            descriptor: IExtractableCodeDescriptor<KaType>, onFinish: (ExtractionResult) -> Unit
        ) {
            val configuration =
                ExtractionGeneratorConfiguration(descriptor as ExtractableCodeDescriptor, withCollapseOption(descriptor.context.project))
            doRefactor(configuration, onFinish)
        }

        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            super<AbstractInplaceExtractionHelper>.configureAndRun(project, editor, descriptorWithConflicts, onFinish)
        }

        @Nls
        override fun getIdentifierError(file: KtFile, variableRange: TextRange): String? {
            val call = PsiTreeUtil.findElementOfClassAtOffset(file, variableRange.startOffset, KtCallExpression::class.java, false) ?: return null
            val name = file.viewProvider.document.getText(variableRange)
            return if (!name.isIdentifier()) {
                JavaRefactoringBundle.message("template.error.invalid.identifier.name")
            } else if (analyzeInModalWindow(file, KotlinBundle.message("fix.change.signature.prepare")) { call.resolveToCall() is KaErrorCallInfo }) {
                JavaRefactoringBundle.message("extract.method.error.method.conflict")
            } else {
                null
            }
        }
    }

    override fun doInvoke(
        editor: Editor,
        file: KtFile,
        elements: List<PsiElement>,
        targetSibling: PsiElement
    ) {

        val adjustedElements = elements.singleOrNull().safeAs<KtBlockExpression>()?.statements ?: elements
        if (adjustedElements.isEmpty()) return
        val data = ActionUtil.underModalProgress(file.project, KotlinBundle.message("fix.change.signature.prepare")) {
            ExtractionData(file, adjustedElements.toRange(false), targetSibling)
        }

        val engine = object :
            IExtractionEngine<KaType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts>(
                helper
            ) {
            override fun performAnalysis(extractionData: ExtractionData): AnalysisResult<KaType> {
                return ExtractionDataAnalyzer(extractionData).performAnalysis()
            }
        }
        engine.run(editor, data)
    }

    override fun restart(
        templateState: TemplateState,
        file: KtFile,
        restartInplace: Boolean
    ): Boolean {
        if (helper is InplaceExtractionHelper) {
            helper.restart(templateState, file, restartInplace)
            return true
        }
        return false
    }
}

private fun withCollapseOption(project: Project): ExtractionGeneratorOptions =
    ExtractionGeneratorOptions.DEFAULT.copy(
        allowExpressionBody = PropertiesComponent.getInstance(project)
            .getBoolean(EXTRACT_FUNCTION_SHOULD_COLLAPSE_BODY, true)
    )