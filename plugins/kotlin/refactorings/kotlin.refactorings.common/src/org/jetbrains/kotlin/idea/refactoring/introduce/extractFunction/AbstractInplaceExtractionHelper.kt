// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction

import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplateBuilder
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import com.intellij.refactoring.extractMethod.newImpl.inplace.TemplateField
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicates
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.math.max
import kotlin.math.min

const val EXTRACT_FUNCTION_SHOULD_COLLAPSE_BODY = "EXTRACT_FUNCTION_COLLAPSE_BODY"

interface AbstractInplaceExtractionHelper<KotlinType,
        Result : IExtractionResult<KotlinType>,
        DescriptorWithConflicts : IExtractableCodeDescriptorWithConflicts<KotlinType>> {

    fun doRefactor(descriptor: IExtractableCodeDescriptor<KotlinType>, onFinish: (Result) -> Unit = {})

    fun createRestartHandler(): AbstractExtractKotlinFunctionHandler

    fun createInplaceRestartHandler(): AbstractExtractKotlinFunctionHandler

    @Nls
    fun getIdentifierError(file: KtFile, variableRange: TextRange): String?

    fun supportConfigurableOptions(): Boolean = true

    fun configureAndRun(
        project: Project,
        editor: Editor,
        descriptorWithConflicts: DescriptorWithConflicts,
        onFinish: (Result) -> Unit
    ) {
        val descriptor = descriptorWithConflicts.descriptor
        val elements = descriptor.extractionData.physicalElements
        val file = descriptor.extractionData.originalFile

        val first = elements.first()
        val callTextRange =
            editor.document.createRangeMarker(
                max(rangeOf(PsiTreeUtil.skipSiblingsBackward(first, PsiComment::class.java) ?: first).startOffset - 1, 0),
                rangeOf(elements.last()).endOffset
            ).apply {
                isGreedyToLeft = true
                isGreedyToRight = true
            }

        val editorState = EditorState(project, editor)
        val disposable = Disposer.newDisposable()
        WriteCommandAction.writeCommandAction(project).run<Throwable> {
            val startMarkAction = StartMarkAction.start(editor, project, EXTRACT_FUNCTION)
            Disposer.register(disposable) { FinishMarkAction.finish(project, editor, startMarkAction) }
        }
        fun afterFinish(extraction: Result) {
            // Templates do not work well in injected editors, see InlayModelWindow
            if (editor is EditorWindow) {
                Disposer.dispose(disposable)
                return
            }
            val callRange: TextRange = callTextRange.textRange
            val callIdentifier = findCallExpressionInRange(file, callRange, extraction.declaration.name)?.calleeExpression ?: throw IllegalStateException()
            val methodIdentifier = extraction.declaration.nameIdentifier ?: throw IllegalStateException()
            val methodRange = extraction.declaration.textRange
            val methodOffset = extraction.declaration.navigationElement.textRange.endOffset
            val callOffset = callIdentifier.textRange.endOffset
            val preview = InplaceExtractUtils.createPreview(editor, methodRange, methodOffset, callRange, callOffset)
            Disposer.register(disposable, preview)
            val shortcut = KeymapUtil.getPrimaryShortcut("ExtractFunction") ?: throw IllegalStateException("Action is not found")
            val templateField = TemplateField(callIdentifier.textRange, listOf(methodIdentifier.textRange))
                .withCompletionNames(descriptor.suggestedNames)
                .withCompletionHint(
                    RefactoringBundle.message(
                        "inplace.refactoring.advertisement.text",
                        KeymapUtil.getShortcutText(shortcut)
                    )
                )
                .withValidation { variableRange ->
                    val error = getIdentifierError(file, variableRange)
                    if (error != null) {
                        InplaceExtractUtils.showErrorHint(editor, variableRange.endOffset, error)
                    }
                    error == null
                }
            val templateState = ExtractMethodTemplateBuilder(editor, EXTRACT_FUNCTION)
                .enableRestartForHandler(createRestartHandler()::class.java)
                .onBroken {
                    editorState.revert()
                }
                .onSuccess {
                    processDuplicates(extraction.duplicateReplacers, project, editor)
                }
                .disposeWithTemplate(disposable)
                .createTemplate(file, listOf(templateField))
            if (supportConfigurableOptions()) {
                afterTemplateStart(templateState, disposable, file)
            }
            onFinish(extraction)
        }
        try {
            doRefactor(descriptor, ::afterFinish)
        } catch (e: Throwable) {
            Disposer.dispose(disposable)
            throw e
        }
    }

    private fun afterTemplateStart(templateState: TemplateState, disposable: Disposable, file: KtFile) {
        val popupProvider = ExtractFunctionPopupProvider(PropertiesComponent.getInstance(templateState.project).getBoolean(EXTRACT_FUNCTION_SHOULD_COLLAPSE_BODY, true))
        popupProvider.setChangeListener {
            val shouldCollapse = popupProvider.expressionBody
            PropertiesComponent.getInstance(templateState.project).setValue(EXTRACT_FUNCTION_SHOULD_COLLAPSE_BODY, shouldCollapse, true)
            restart(templateState, file, true)
        }

        val editor = templateState.editor as? EditorImpl ?: return
        val offset = templateState.currentVariableRange?.endOffset ?: return

        val presentation = TemplateInlayUtil.createSettingsPresentation(editor)
        val templateElement = TemplateInlayUtil.SelectableTemplateElement(presentation)
        TemplateInlayUtil.createNavigatableButtonWithPopup(
            templateState.editor, offset, presentation, popupProvider.panel,
            templateElement, isPopupAbove = false
        )?.let {
            Disposer.register(disposable, it)
        }
    }

    fun restart(templateState: TemplateState, file: KtFile, restartInplace: Boolean) {
        val editor = templateState.editor
        templateState.gotoEnd(true)
        val handler = if (restartInplace) createInplaceRestartHandler() else createRestartHandler()
        handler.invoke(file.project, editor, file, null)
    }

    fun rangeOf(element: PsiElement): TextRange {
        return (element as? KtExpression)?.extractableSubstringInfo?.contentRange ?: element.textRange
    }

    fun createSmartRangeProvider(container: PsiElement, range: TextRange): () -> TextRange? {
        val offsetFromStart = range.startOffset - container.textRange.startOffset
        val offsetFromEnd = container.textRange.endOffset - range.endOffset
        val pointer = SmartPointerManager.createPointer(container)
        fun findRange(): TextRange? {
            val containerRange = pointer.range ?: return null
            return TextRange(containerRange.startOffset + offsetFromStart, containerRange.endOffset - offsetFromEnd)
        }
        return ::findRange
    }

    /**
     * Finds the first occurrence of a call expression within the given range.
     * If [name] is specified, first occurrence with the given reference name would be taken.
     *
     * Range is created from the previous element of the extraction, and thus when the function is inserted before the selection,
     * e.g., when extracted function is added as local function, range contains extracted function and the call to it.
     * So we need additional ad hock filtering to prevent broken code on rename template start.
     */
    fun findCallExpressionInRange(file: KtFile, range: TextRange?, name: @NlsSafe String?): KtCallExpression? {
        if (range == null) return null
        val container = PsiTreeUtil.findCommonParent(
            file.findElementAt(range.startOffset), file.findElementAt(min(file.textLength - 1, range.endOffset))
        )
        val callExpressions = PsiTreeUtil.findChildrenOfType(container, KtCallExpression::class.java)
        val callExpressionsInRange = callExpressions.filter { it.textRange in range }
        if (name == null) return callExpressionsInRange.firstOrNull()
        return callExpressionsInRange.find { (it.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == name }
            ?: callExpressionsInRange.firstOrNull()
    }
}

private class ExtractFunctionPopupProvider(expressionBodyDefault: Boolean) {
    var expressionBody = expressionBodyDefault
        private set

    private var changeListener: () -> Unit = {}

    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
    }

    val panel: JPanel by lazy { createPanel() }

    private fun createPanel(): DialogPanel {
        val panel = panel {
            row {
                checkBox(KotlinBundle.message("checkbox.collapse.to.expression.body"))
                    .selected(expressionBody)
                    .onChanged { component ->
                        expressionBody = component.isSelected
                        changeListener.invoke()
                    }
            }
        }
        panel.isFocusCycleRoot = true
        panel.focusTraversalPolicy = LayoutFocusTraversalPolicy()
        panel.preferredFocusedComponent = UIUtil.findComponentOfType(panel, JCheckBox::class.java)

        return panel
    }
}