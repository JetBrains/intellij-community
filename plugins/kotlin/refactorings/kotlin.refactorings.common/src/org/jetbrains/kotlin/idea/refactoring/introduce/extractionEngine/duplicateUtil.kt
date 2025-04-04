// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.find.FindManager
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler
import com.intellij.ui.ReplacePromptDialog
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult.StrictSuccess
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult.WeakSuccess
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.ExpressionValue
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.Jump
import org.jetbrains.kotlin.idea.refactoring.introduce.getPhysicalTextRange
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

fun KotlinPsiRange.highlight(project: Project, editor: Editor): RangeHighlighter? {
    val textRange = getPhysicalTextRange()
    val highlighters = ArrayList<RangeHighlighter>()
    HighlightManager.getInstance(project).addRangeHighlight(
        editor, textRange.startOffset, textRange.endOffset, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters
    )
    return highlighters.firstOrNull()
}

fun KotlinPsiRange.preview(project: Project, editor: Editor): RangeHighlighter? {
    val highlight = highlight(project, editor) ?: return null

    val startOffset = getPhysicalTextRange().startOffset
    val foldedRegions = CodeFoldingManager.getInstance(project)
        .getFoldRegionsAtOffset(editor, startOffset)
        .filter { !it.isExpanded }

    if (foldedRegions.isNotEmpty()) {
        editor.foldingModel.runBatchFoldingOperation {
            foldedRegions.forEach { it.isExpanded = true }
        }
    }

    editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(startOffset), ScrollType.MAKE_VISIBLE)
    return highlight
}

fun processDuplicates(
    duplicateReplacers: Map<KotlinPsiRange, () -> Unit>,
    project: Project,
    editor: Editor,
    scopeDescription: String = "this file",
    usageDescription: String = "a usage of extracted declaration"
) {
    val size = duplicateReplacers.size
    if (size == 0) return

    if (size == 1) {
        duplicateReplacers.keys.first().preview(project, editor)
    }

    val answer = if (isUnitTestMode()) {
        Messages.YES
    } else {
        Messages.showYesNoDialog(
            project,
            KotlinBundle.message("0.has.detected.1.code.fragments.in.2.that.can.be.replaced.with.3",
                                 ApplicationNamesInfo.getInstance().productName,
                                 duplicateReplacers.size,
                                 scopeDescription,
                                 usageDescription
            ),
            KotlinBundle.message("text.process.duplicates"),
            Messages.getQuestionIcon()
        )
    }

    if (answer != Messages.YES) {
        return
    }

    var showAll = false

    duplicateReplacersLoop@
    for ((i, entry) in duplicateReplacers.entries.withIndex()) {
        val (pattern, replacer) = entry
        if (!pattern.isValid) continue

        val highlighter = pattern.preview(project, editor)
        if (!isUnitTestMode()) {
            if (size > 1 && !showAll) {
                val promptDialog = ReplacePromptDialog(false, JavaRefactoringBundle.message("process.duplicates.title", i + 1, size), project)
                promptDialog.show()
                when (promptDialog.exitCode) {
                    FindManager.PromptResult.ALL -> showAll = true
                    FindManager.PromptResult.SKIP -> continue@duplicateReplacersLoop
                    FindManager.PromptResult.CANCEL -> return
                }
            }
        }

        highlighter?.let { HighlightManager.getInstance(project).removeSegmentHighlighter(editor, it) }
        project.executeWriteCommand(MethodDuplicatesHandler.getRefactoringName(), replacer)
    }
}

fun processDuplicatesSilently(duplicateReplacers: Map<KotlinPsiRange, () -> Unit>, project: Project) {
    project.executeWriteCommand(MethodDuplicatesHandler.getRefactoringName()) {
        duplicateReplacers.values.forEach { it() }
    }
}


fun <KotlinType> IExtractableCodeDescriptor<KotlinType>.processWeakMatch(
    match: WeakSuccess<*>,
    newControlFlow: ControlFlow<KotlinType>
): Boolean {
    val valueCount = controlFlow.outputValues.size

    val weakMatches = HashMap(match.weakMatches)
    val currentValuesToNew = HashMap<OutputValue<KotlinType>, OutputValue<KotlinType>>()

    fun matchValues(currentValue: OutputValue<KotlinType>, newValue: OutputValue<KotlinType>): Boolean {
        if ((currentValue is Jump) != (newValue is Jump)) return false
        if (currentValue.originalExpressions.zip(newValue.originalExpressions).all { weakMatches[it.first] == it.second }) {
            currentValuesToNew[currentValue] = newValue
            weakMatches.keys.removeAll(currentValue.originalExpressions)
            return true
        }
        return false
    }

    if (valueCount == 1) {
        matchValues(controlFlow.outputValues.first(), newControlFlow.outputValues.first())
    } else {
        outer@
        for (currentValue in controlFlow.outputValues)
            for (newValue in newControlFlow.outputValues) {
                if ((currentValue is ExpressionValue<KotlinType>) != (newValue is ExpressionValue<KotlinType>)) continue
                if (matchValues(currentValue, newValue)) continue@outer
            }
    }

    return currentValuesToNew.size == valueCount && weakMatches.isEmpty()
}

fun <KotlinType> IExtractableCodeDescriptor<KotlinType>.getControlFlowIfMatched(
    match: KotlinPsiUnificationResult.Success<*>,
    analysisResult: AnalysisResult<KotlinType>
): ControlFlow<KotlinType>? {
    if (analysisResult.status != AnalysisResult.Status.SUCCESS) return null

    val newControlFlow = analysisResult.descriptor!!.controlFlow
    if (newControlFlow.outputValues.isEmpty()) return newControlFlow
    if (controlFlow.outputValues.size != newControlFlow.outputValues.size) return null

    val matched = when (match) {
        is StrictSuccess -> true
        is WeakSuccess -> processWeakMatch(match, newControlFlow)
        else -> throw AssertionError("Unexpected unification result: $match")
    }

    return if (matched) newControlFlow else null
}