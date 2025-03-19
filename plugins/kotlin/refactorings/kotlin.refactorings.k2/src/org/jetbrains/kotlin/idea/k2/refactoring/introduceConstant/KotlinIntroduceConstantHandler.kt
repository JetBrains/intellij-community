// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionData
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionDataAnalyzer
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.validate
import org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty.CONTAINER_KEY
import org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty.INTRODUCE_PROPERTY
import org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty.KotlinInplacePropertyIntroducer
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionTarget
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHintByKey
import org.jetbrains.kotlin.idea.refactoring.introduce.validateExpressionElements
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinIntroduceConstantHandler(
    val helper: ExtractionEngineHelper = InteractiveExtractionHelper
) : RefactoringActionHandler {

    object InteractiveExtractionHelper : InteractiveExtractionHelperWithOptions()

    open class InteractiveExtractionHelperWithOptions(val currentTarget: ExtractionTarget? = null) : ExtractionEngineHelper(INTRODUCE_PROPERTY) {
        private fun getExtractionTarget(descriptor: ExtractableCodeDescriptor) =
            propertyTargets.firstOrNull { it.isAvailable(descriptor) && (currentTarget == null || it == currentTarget) }

        override fun validate(descriptor: ExtractableCodeDescriptor) =
            descriptor.validate(getExtractionTarget(descriptor) ?: ExtractionTarget.FUNCTION)

        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            val descriptor = descriptorWithConflicts.descriptor
            val target = getExtractionTarget(descriptor)
            if (target != null) {
                val options = ExtractionGeneratorOptions(target = target, delayInitialOccurrenceReplacement = true, isConst = true)
                doRefactor(ExtractionGeneratorConfiguration(descriptor, options), onFinish)
            } else {
                showErrorHint(
                    project,
                    editor,
                    KotlinBundle.message("error.text.can.t.introduce.constant.for.this.expression"),
                    INTRODUCE_CONSTANT
                )
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun doInvoke(project: Project, editor: Editor, file: KtFile, elements: List<PsiElement>, target: PsiElement) {
        val adjustedElements = (elements.singleOrNull() as? KtBlockExpression)?.statements ?: elements
        when {
            adjustedElements.isEmpty() -> {
                showErrorHintByKey(
                    project, editor, "cannot.refactor.no.expression",
                    INTRODUCE_CONSTANT
                )
            }

            else -> {
                val extractionData = ActionUtil.underModalProgress(file.project, KotlinBundle.message("fix.change.signature.prepare")) {
                    val options = ExtractionOptions(extractAsProperty = true)
                    ExtractionData(file, adjustedElements.toRange(), target, null, options)
                }
                val engine = object :
                    IExtractionEngine<KaType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts>(
                        helper
                    ) {
                    override fun performAnalysis(extractionData: ExtractionData): AnalysisResult<KaType> {
                        return ExtractionDataAnalyzer(extractionData).performAnalysis()
                    }
                }
                val editorState = EditorState(project, editor)
                engine.run(editor, extractionData) { extractResult ->
                    val property = extractResult.declaration as KtProperty
                    val descriptor = extractResult.config.descriptor
                    val exprType =
                        allowAnalysisOnEdt { analyze(property) { CallableReturnTypeUpdaterUtils.TypeInfo.createByKtTypes(property.returnType) } }

                    val introducer = object : KotlinInplacePropertyIntroducer(
                        property = property,
                        editor = editor,
                        project = project,
                        title = INTRODUCE_CONSTANT,
                        doNotChangeVar = false,
                        exprType = exprType,
                        extractionResult = extractResult,
                        availableTargets = listOf(ExtractionTarget.PROPERTY_WITH_GETTER),
                        replaceAllByDefault = helper.replaceAllByDefault()
                    ) {
                        override fun onCancel(restart: Boolean) {
                            if (restart) {
                                property.parent.putUserData(CONTAINER_KEY, Unit)
                            }
                            editorState.revert()
                        }
                    }

                    editor.caretModel.moveToOffset(property.textOffset)
                    editor.selectionModel.removeSelection()
                    if (editor.settings.isVariableInplaceRenameEnabled && !isUnitTestMode()) {
                        with(PsiDocumentManager.getInstance(project)) {
                            commitDocument(editor.document)
                            doPostponedOperationsAndUnblockDocument(editor.document)
                        }
                        introducer.performInplaceRefactoring(LinkedHashSet(getNameSuggestions(property) + descriptor.suggestedNames))
                    } else {
                        introducer.performRefactoring()
                    }
                }
            }
        }
    }

    private fun getNameSuggestions(property: KtProperty): List<String> {
        val initializerValue = property.initializer.safeAs<KtStringTemplateExpression>()?.plainContent
        val identifierValue = property.identifyingElement?.text

        return listOfNotNull(initializerValue, identifierValue).map { NameUtil.capitalizeAndUnderscore(it) }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return
        selectElements(editor, file) { elements, targets -> doInvoke(project, editor, file, elements, targets) }
    }

    fun selectElements(
        editor: Editor,
        file: KtFile,
        continuation: (elements: List<PsiElement>, targets: PsiElement) -> Unit
    ) {

        selectElementsWithTargetSibling(
            INTRODUCE_CONSTANT,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(ElementKind.EXPRESSION),
            ::validateElements,
            { _, sibling ->
                val containers = sibling.getExtractionContainers(strict = true, includeAll = true)
                    .filter { (it is KtFile && !it.isScript()) }
                containers.singleOrNull { container ->
                    val theContainer = container.getUserData(CONTAINER_KEY) != null
                    if (theContainer) {
                        container.putUserData(CONTAINER_KEY, null)
                    }
                    theContainer
                }?.let { listOf(it) } ?: containers
            },
            continuation
        )
    }

    private fun validateElements(elements: List<PsiElement>): String? {
        val errorMessage = validateExpressionElements(elements)
        return when {
            errorMessage != null -> errorMessage
            elements.any {
                // unchecked cast always succeeds because only expressions are selected in selectElements
                (it as KtExpression).isNotConst()
            } -> KotlinBundle.message(
                "error.text.can.t.introduce.constant.for.this.expression.because.not.constant"
            )

            else -> null
        }
    }

    private fun KtExpression.isNotConst(): Boolean {
        return when (this) {
            is KtConstantExpression -> false
            else -> {
                analyzeInModalWindow(this, KotlinBundle.message("fix.change.signature.prepare")) {
                    this@isNotConst.evaluate()
                } == null
            }
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("$INTRODUCE_CONSTANT can only be invoked from editor")
    }
}

val INTRODUCE_CONSTANT: String
    @Nls
    get() = KotlinBundle.message("introduce.constant")
