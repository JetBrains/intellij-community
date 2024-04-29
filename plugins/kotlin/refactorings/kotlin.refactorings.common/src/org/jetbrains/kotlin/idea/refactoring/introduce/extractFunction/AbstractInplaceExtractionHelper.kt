// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplateBuilder
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import com.intellij.refactoring.extractMethod.newImpl.inplace.TemplateField
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicates
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import kotlin.math.min

interface AbstractInplaceExtractionHelper<KotlinType,
        Result : IExtractionResult<KotlinType>,
        DescriptorWithConflicts : IExtractableCodeDescriptorWithConflicts<KotlinType>> {

    fun doRefactor(descriptor: IExtractableCodeDescriptor<KotlinType>, onFinish: (Result) -> Unit = {})

    fun createRestartHandler(): AbstractExtractKotlinFunctionHandler

    @Nls
    fun getIdentifierError(file: KtFile, variableRange: TextRange): String?


    fun configureAndRun(
        project: Project,
        editor: Editor,
        descriptorWithConflicts: DescriptorWithConflicts,
        onFinish: (Result) -> Unit
    ) {
        val activeTemplateState = TemplateManagerImpl.getTemplateState(editor)
        if (activeTemplateState != null) {
            activeTemplateState.gotoEnd(true)
            createRestartHandler()
                .invoke(project, editor, descriptorWithConflicts.descriptor.extractionData.originalFile, null)
        }
        val descriptor = descriptorWithConflicts.descriptor
        val elements = descriptor.extractionData.physicalElements
        val file = descriptor.extractionData.originalFile
        val callTextRange =
            editor.document.createRangeMarker(
                rangeOf(elements.first()).startOffset,
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
            val callIdentifier = findCallExpressionInRange(file, callRange)?.calleeExpression ?: throw IllegalStateException()
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
            ExtractMethodTemplateBuilder(editor, EXTRACT_FUNCTION)
                .enableRestartForHandler(createRestartHandler()::class.java)
                .onBroken {
                    editorState.revert()
                }
                .onSuccess {
                    processDuplicates(extraction.duplicateReplacers, project, editor)
                }
                .disposeWithTemplate(disposable)
                .createTemplate(file, listOf(templateField))
            onFinish(extraction)
        }
        try {
            doRefactor(descriptor, ::afterFinish)
        } catch (e: Throwable) {
            Disposer.dispose(disposable)
            throw e
        }
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


    fun findCallExpressionInRange(file: KtFile, range: TextRange?): KtCallExpression? {
        if (range == null) return null
        val container = PsiTreeUtil.findCommonParent(file.findElementAt(range.startOffset),
                                                     file.findElementAt(min(file.textLength - 1, range.endOffset)))
        val callExpressions = PsiTreeUtil.findChildrenOfType(container, KtCallExpression::class.java)
        return callExpressions.firstOrNull { it.textRange in range }
    }
}