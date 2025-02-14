// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaType
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
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHintByKey
import org.jetbrains.kotlin.idea.refactoring.introduce.validateExpressionElements
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

internal val CONTAINER_KEY = Key.create<Unit>("PARENT_CONTAINER")

class KotlinIntroducePropertyHandler(
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
                val options = ExtractionGeneratorOptions.DEFAULT.copy(target = target, delayInitialOccurrenceReplacement = true)
                doRefactor(ExtractionGeneratorConfiguration(descriptor, options), onFinish)
            } else {
                showErrorHint(
                    project,
                    editor,
                    KotlinBundle.message("error.text.can.t.introduce.property.for.this.expression"),
                    INTRODUCE_PROPERTY
                )
            }
        }
    }

    fun selectElements(editor: Editor, file: KtFile, continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit) {
        selectElementsWithTargetSibling(
            INTRODUCE_PROPERTY,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(ElementKind.EXPRESSION),
            ::validateExpressionElements,
            { _, parent ->
                val containers = parent.getExtractionContainers(strict = true, includeAll = true)
                    .filter { it is KtClassBody || (it is KtFile && !it.isScript()) }
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

    fun doInvoke(project: Project, editor: Editor, file: KtFile, elements: List<PsiElement>, targetSibling: PsiElement) {
        val adjustedElements = (elements.singleOrNull() as? KtBlockExpression)?.statements ?: elements
        if (adjustedElements.isNotEmpty()) {
            val extractionData = ActionUtil.underModalProgress(file.project, KotlinBundle.message("fix.change.signature.prepare")) {
                val options = ExtractionOptions(extractAsProperty = true)
                ExtractionData(file, adjustedElements.toRange(), targetSibling, null, options)
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
            engine.run(editor, extractionData) {
                val property = it.declaration as KtProperty
                val descriptor = it.config.descriptor
                val offset = property.textOffset
                editor.caretModel.moveToOffset(offset)
                editor.selectionModel.removeSelection()

                val component = editor.component
                val point = RelativePoint(component, editor.logicalPositionToXY(editor.offsetToLogicalPosition(offset)))
                val exprType = Utils.computeWithProgressIcon(point, component, ActionPlaces.UNKNOWN) {
                    readAction { analyze (property) { CallableReturnTypeUpdaterUtils.TypeInfo.createByKtTypes(property.returnType) } }
                }

                if (editor.settings.isVariableInplaceRenameEnabled && !isUnitTestMode()) {
                    with(PsiDocumentManager.getInstance(project)) {
                        commitDocument(editor.document)
                        doPostponedOperationsAndUnblockDocument(editor.document)
                    }

                    val introducer = object : KotlinInplacePropertyIntroducer(
                        property = property,
                        editor = editor,
                        project = project,
                        title = INTRODUCE_PROPERTY,
                        doNotChangeVar = false,
                        exprType = exprType,
                        extractionResult = it,
                        availableTargets = propertyTargets.filter { target -> target.isAvailable(descriptor) },
                    ) {
                        override fun onCancel(restart: Boolean) {
                            if (restart) {
                                property.parent.putUserData(CONTAINER_KEY, Unit)
                            }
                            editorState.revert()
                        }
                    }

                    introducer.performInplaceRefactoring(LinkedHashSet(descriptor.suggestedNames))
                } else {
                    processDuplicatesSilently(it.duplicateReplacers, project)
                }
            }
        } else {
            showErrorHintByKey(project, editor, "cannot.refactor.no.expression", INTRODUCE_PROPERTY)
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return
        selectElements(editor, file) { elements, targetSibling -> doInvoke(project, editor, file, elements, targetSibling) }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("$INTRODUCE_PROPERTY can only be invoked from editor")
    }
}

val INTRODUCE_PROPERTY: String
    @Nls
    get() = KotlinBundle.message("introduce.property")