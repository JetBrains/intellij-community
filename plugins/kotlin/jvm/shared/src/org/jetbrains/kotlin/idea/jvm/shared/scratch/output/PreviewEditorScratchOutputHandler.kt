// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.output

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Graphics
import java.awt.Rectangle
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import java.util.NavigableMap
import java.util.TreeMap
import kotlin.math.max

/**
 * Output handler to print scratch output to separate window using [previewOutputBlocksManager].
 *
 * Multiline values are rendered via block inlays (preserving line-number alignment).
 * A gutter icon ([InlayToggleGutterRenderer]) on each multiline line lets the user expand/collapse
 * the inlay showing the remaining value lines.
 */
class PreviewEditorScratchOutputHandler(
    private val previewOutputBlocksManager: PreviewOutputBlocksManager,
    private val toolwindowHandler: ScratchOutputHandler,
    private val parentDisposable: Disposable
) : ScratchOutputHandlerAdapter() {

    override fun onStart(file: ScratchFile) {
        toolwindowHandler.onStart(file)
        clearOutputManager()
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        printToPreviewEditor(expression, output)
    }

    override fun handle(file: ScratchFile, output: ScratchOutput) {
        toolwindowHandler.handle(file, output)
    }

    override fun handle(file: ScratchFile, explanations: List<ExplainInfo>, scope: CoroutineScope) {
        previewOutputBlocksManager.addOutput(explanations, scope)
    }

    override fun error(file: ScratchFile, message: String) {
        toolwindowHandler.error(file, message)
    }

    override fun onFinish(file: ScratchFile) {
        toolwindowHandler.onFinish(file)
    }

    override fun clear(file: ScratchFile) {
        toolwindowHandler.clear(file)

        clearOutputManager()
    }

    private fun printToPreviewEditor(expression: ScratchExpression, output: ScratchOutput) {
        TransactionGuard.submitTransaction(parentDisposable, Runnable {
            val targetCell = previewOutputBlocksManager.getBlock(expression) ?: previewOutputBlocksManager.addBlockToTheEnd(expression)
            targetCell.addOutput(output)
        })
    }

    private fun clearOutputManager() {
        TransactionGuard.submitTransaction(parentDisposable, Runnable {
            previewOutputBlocksManager.clear()
        })
    }
}

private val ScratchExpression.height: Int get() = lineEnd - lineStart + 1

interface ScratchOutputBlock {
    val sourceExpression: ScratchExpression
    val lineStart: Int
    val lineEnd: Int
    fun addOutput(output: ScratchOutput)
}

class PreviewOutputBlocksManager(editor: Editor) {
    private val targetDocument: Document = editor.document
    private val foldingModel: FoldingModel = editor.foldingModel
    private val inlayModel = editor.inlayModel
    private val markupModel: MarkupModel = editor.markupModel
    private val project: Project? = editor.project

    private val blocks: NavigableMap<ScratchExpression, OutputBlock> = TreeMap(Comparator.comparingInt { it.lineStart })
    private val explainInlays: MutableList<Inlay<MultiLineInlayRenderer>> = mutableListOf()
    private val explainHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    fun computeSourceToPreviewAlignments(): List<Pair<Int, Int>> = blocks.values.map { it.sourceExpression.lineStart to it.lineStart }

    fun getBlock(expression: ScratchExpression): ScratchOutputBlock? = blocks[expression]

    fun addBlockToTheEnd(expression: ScratchExpression): ScratchOutputBlock = OutputBlock(expression).also {
        if (blocks.putIfAbsent(expression, it) != null) {
            error("There is already a cell for $expression!")
        }
    }

    fun addOutput(infos: List<ExplainInfo>, scope: CoroutineScope) {
        val project = project ?: return

        scope.launch {
            writeCommandAction(project, KotlinJvmBundle.message("command.name.processing.kotlin.scratch.output")) {
                explainInlays.forEach { Disposer.dispose(it) }
                explainInlays.clear()
                explainHighlighters.forEach { markupModel.removeHighlighter(it) }
                explainHighlighters.clear()
                targetDocument.setText("")
                insertFormattedLines(infos)
            }
        }
    }

    private fun insertFormattedLines(infos: List<ExplainInfo>) {
        val entries = infos
            .filter { it.variableValue !in VALUES_TO_HIDE }
            .sortedWith(compareBy({ it.line ?: Int.MAX_VALUE }, { it.offsets.second }))

        if (entries.isEmpty()) return

        val attributes = getAttributesForOutputType(ScratchOutputType.RESULT)
        var prevDocEnd = -1

        // (targetDocLine, inlayLines, expandedChainText) — collected during the text-insertion
        // pass and processed in a second pass once all document text is in place, so that
        // subsequent insertions cannot displace already-created inlays.
        // expandedChainText is the chain text without "..." placeholders (shown when expanded).
        val pendingInlays = mutableListOf<Triple<Int, List<String>, String>>()

        // Pass 1: insert all chain text and inline folds; collect pending inlays.
        for ((sourceLine, group) in entries.groupConsecutiveBy { it.line }) {
            if (sourceLine == null) continue

            // Each entry contributes one part to the chain; multiline values use "firstLine..."
            // in the collapsed chain and spill their remaining lines into the inlay.
            val chainParts = mutableListOf<String>()       // first line of each entry (no "...")
            val chainPartsMultiline = mutableListOf<Boolean>()
            val inlayLines = mutableListOf<String>()

            for (entry in group) {
                val fullText = formatSingleVariable(entry.variableName, entry.variableValue)
                if (fullText.isBlank()) continue
                val lines = fullText.split('\n')
                if (lines.size > 1) {
                    chainParts.add(lines.first())
                    chainPartsMultiline.add(true)
                    inlayLines.addAll(lines.drop(1))
                } else {
                    chainParts.add(lines.first())
                    chainPartsMultiline.add(false)
                }
            }

            if (chainParts.isEmpty()) continue

            // Collapsed form: multiline parts get "..." suffix. Expanded form: no suffixes.
            val collapsedChainText = chainParts.indices.joinToString(" → ") { i ->
                if (chainPartsMultiline[i]) "${chainParts[i]}..." else chainParts[i]
            }
            val expandedChainText = chainParts.joinToString(" → ")

            val targetDocLine = max(sourceLine, prevDocEnd + 1)
            targetDocument.insertStringAtLine(targetDocLine, collapsedChainText)
            markupModel.highlightLines(targetDocLine, targetDocLine, attributes)

            if (inlayLines.isNotEmpty()) {
                pendingInlays.add(Triple(targetDocLine, inlayLines.toList(), expandedChainText))
            }

            prevDocEnd = targetDocLine
        }

        // Pass 2: create inlays and gutter renderers now that all document text is final.
        // No more insertions will happen, so inlay offsets are stable regardless of
        // relatesToPrecedingText.
        for ((targetDocLine, inlayLines, expandedChainText) in pendingInlays) {
            val lineStart = targetDocument.getLineStartOffset(targetDocLine)
            val collapsedChainText = targetDocument.text.substring(
                lineStart, targetDocument.getLineEndOffset(targetDocLine)
            )
            val offset = targetDocument.getLineEndOffset(targetDocLine)
            val renderer = MultiLineInlayRenderer(inlayLines, attributes)
            val inlay = inlayModel.addBlockElement(offset, true, false, 0, renderer)
            if (inlay != null) {
                explainInlays.add(inlay)
                val toggleRenderer = InlayToggleGutterRenderer(inlay, targetDocLine, expandedChainText, collapsedChainText)
                val highlighter = markupModel.addRangeHighlighter(
                    lineStart,
                    targetDocument.getLineEndOffset(targetDocLine),
                    HighlighterLayer.SELECTION,
                    null,
                    HighlighterTargetArea.EXACT_RANGE
                )
                toggleRenderer.highlighter = highlighter
                highlighter.gutterIconRenderer = toggleRenderer
                explainHighlighters.add(highlighter)
            }
        }
    }

    private fun formatSingleVariable(variableName: String, variableValue: Any?): String =
        if (variableName.isNotBlank() && variableName != RESULT) {
            "$variableName: ${variableValue}"
        } else {
            "${variableValue}"
        }

    fun clear() {
        blocks.clear()
        explainInlays.forEach { Disposer.dispose(it) }
        explainInlays.clear()
        explainHighlighters.forEach { markupModel.removeHighlighter(it) }
        explainHighlighters.clear()
        runWriteAction {
            executeCommand {
                targetDocument.setText("")
            }
        }
    }

    private inner class OutputBlock(override val sourceExpression: ScratchExpression) : ScratchOutputBlock {
        private val outputs: MutableList<ScratchOutput> = mutableListOf()

        override var lineStart: Int = computeCellLineStart(sourceExpression)
            private set

        override val lineEnd: Int get() = lineStart + countNewLines(outputs)

        val height: Int get() = lineEnd - lineStart + 1
        private var foldRegion: FoldRegion? = null

        override fun addOutput(output: ScratchOutput) {
            printAndSaveOutput(output)

            blocks.lowerEntry(sourceExpression)?.value?.updateFolding()
            blocks.tailMap(sourceExpression).values.forEach {
                it.recalculatePosition()
                it.updateFolding()
            }
        }

        /**
         * We want to make sure that changes in document happen in single edit, because if they are not,
         * listeners may see inconsistent document, which may cause troubles if they will try to highlight it
         * in some way. That's why it is important that [insertStringAtLine] does only one insert in the document,
         * and [output] is inserted into the [outputs] before the edits, so [OutputBlock] can correctly see
         * all its output expressions and highlight the whole block.
         */
        private fun printAndSaveOutput(output: ScratchOutput) {
            val beforeAdding = lineEnd
            val currentOutputStartLine = if (outputs.isEmpty()) lineStart else beforeAdding + 1

            outputs.add(output)

            runWriteAction {
                executeCommand {
                    targetDocument.insertStringAtLine(currentOutputStartLine, output.text)
                }
            }

            markupModel.highlightLines(currentOutputStartLine, lineEnd, getAttributesForOutputType(output.type))
        }

        private fun recalculatePosition() {
            lineStart = computeCellLineStart(sourceExpression)
        }

        private fun updateFolding() {
            foldingModel.runBatchFoldingOperation {
                foldRegion?.let(foldingModel::removeFoldRegion)

                if (height <= sourceExpression.height) return@runBatchFoldingOperation

                val firstFoldedLine = lineStart + (sourceExpression.height - 1)
                val placeholderLine = "${targetDocument.getLineContent(firstFoldedLine)}..."

                foldRegion = foldingModel.addFoldRegion(
                    targetDocument.getLineStartOffset(firstFoldedLine),
                    targetDocument.getLineEndOffset(lineEnd),
                    placeholderLine
                )

                foldRegion?.isExpanded = false
            }
        }

    }

    private fun computeCellLineStart(scratchExpression: ScratchExpression): Int {
        val previous = blocks.lowerEntry(scratchExpression)?.value ?: return scratchExpression.lineStart

        val distanceBetweenSources = scratchExpression.lineStart - previous.sourceExpression.lineEnd
        val differenceBetweenSourceAndOutputHeight = previous.sourceExpression.height - previous.height
        val compensation = max(differenceBetweenSourceAndOutputHeight, 0)
        return previous.lineEnd + compensation + distanceBetweenSources
    }

    fun getBlockAtLine(line: Int): ScratchOutputBlock? = blocks.values.find { line in it.lineStart..it.lineEnd }

    /**
     * Returns the full explain content for testing: each document line followed by its
     * inlay lines (if any), prefixed with two spaces to distinguish them from chain lines.
     */
    fun dumpContent(): String = buildString {
        val inlayByLine = explainInlays
            .filter { it.isValid }
            .associateBy { targetDocument.getLineNumber(it.offset) }
        for (line in 0 until targetDocument.lineCount) {
            if (line > 0) append('\n')
            append(
                targetDocument.text.substring(
                    targetDocument.getLineStartOffset(line),
                    targetDocument.getLineEndOffset(line)
                )
            )
            val inlay = inlayByLine[line] ?: continue
            for (inlayLine in inlay.renderer.lines) {
                append('\n')
                append("  ")
                append(inlayLine)
            }
        }
    }
}

private fun countNewLines(list: List<ScratchOutput>) = list.sumOf { StringUtil.countNewLines(it.text) } + max(list.size - 1, 0)

private fun Document.getLineContent(lineNumber: Int) =
    DiffUtil.getLinesContent(this, lineNumber, lineNumber + 1).toString()

private fun Document.insertStringAtLine(lineNumber: Int, text: String) {
    val missingNewLines = lineNumber - (DiffUtil.getLineCount(this) - 1)
    if (missingNewLines > 0) {
        insertString(textLength, "${"\n".repeat(missingNewLines)}$text")
    } else {
        insertString(getLineStartOffset(lineNumber), text)
    }
}

fun MarkupModel.highlightLines(
    from: Int,
    to: Int,
    attributes: TextAttributes,
    targetArea: HighlighterTargetArea = HighlighterTargetArea.EXACT_RANGE
): RangeHighlighter {
    val fromOffset = document.getLineStartOffset(from)
    val toOffset = document.getLineEndOffset(to)

    return addRangeHighlighter(
        fromOffset,
        toOffset,
        HighlighterLayer.CARET_ROW,
        attributes,
        targetArea
    )
}

/**
 * Groups consecutive elements that share the same key, preserving order.
 * Unlike [groupBy], this keeps separate groups for non-adjacent equal keys.
 */
private fun <T, K> List<T>.groupConsecutiveBy(key: (T) -> K): List<Pair<K, List<T>>> = buildList {
    var currentKey: K? = null
    var currentGroup = mutableListOf<T>()
    for (item in this@groupConsecutiveBy) {
        val k = key(item)
        if (k != currentKey) {
            @Suppress("UNCHECKED_CAST")
            if (currentGroup.isNotEmpty()) add(currentKey as K to currentGroup.toList())
            currentKey = k
            currentGroup = mutableListOf()
        }
        currentGroup.add(item)
    }
    @Suppress("UNCHECKED_CAST")
    if (currentGroup.isNotEmpty()) add(currentKey as K to currentGroup.toList())
}

private val VALUES_TO_HIDE = setOf("kotlin.Unit", "kotlin.Nothing")

private const val RESULT = $$$"$$result"

private class InlayToggleGutterRenderer(
    private val inlay: Inlay<MultiLineInlayRenderer>,
    private val lineIndex: Int,
    private val expandedText: String,
    private val collapsedText: String
) : GutterIconRenderer() {
    var highlighter: RangeHighlighter? = null

    override fun getIcon(): Icon =
        if (inlay.renderer.isExpanded) AllIcons.Actions.Collapseall
        else AllIcons.Actions.Expandall

    override fun getTooltipText(): String =
        if (inlay.renderer.isExpanded) KotlinJvmBundle.message("scratch.explain.inlay.collapse")
        else KotlinJvmBundle.message("scratch.explain.inlay.expand")

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val newExpanded = !inlay.renderer.isExpanded
            inlay.renderer.isExpanded = newExpanded
            inlay.update()
            // Replace the chain line with the collapsed/expanded version so that all
            // "..." placeholders are added or removed in one operation.
            runWriteAction {
                val doc = inlay.editor.document
                val lineStart = doc.getLineStartOffset(lineIndex)
                val lineEnd = doc.getLineEndOffset(lineIndex)
                doc.replaceString(lineStart, lineEnd, if (newExpanded) expandedText else collapsedText)
            }
            val h = highlighter ?: return
            h.gutterIconRenderer = null
            h.gutterIconRenderer = this@InlayToggleGutterRenderer
        }
    }

    override fun isNavigateAction(): Boolean = true
    override fun getAlignment(): Alignment = Alignment.RIGHT
    override fun equals(other: Any?): Boolean =
        other is InlayToggleGutterRenderer && other.inlay === inlay

    override fun hashCode(): Int = inlay.hashCode()
}

class MultiLineInlayRenderer(
    internal val lines: List<String>,
    private val attributes: TextAttributes
) : EditorCustomElementRenderer {
    var isExpanded: Boolean = false

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0

    override fun calcHeightInPixels(inlay: Inlay<*>): Int =
        if (isExpanded) inlay.editor.lineHeight * lines.size else 0

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        if (!isExpanded || lines.isEmpty()) return
        val fgColor = attributes.foregroundColor ?: return
        val editor = inlay.editor
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val fm = editor.contentComponent.getFontMetrics(font)
        g.color = fgColor
        g.font = font
        val lineHeight = editor.lineHeight
        for ((i, line) in lines.withIndex()) {
            g.drawString(line, targetRegion.x, targetRegion.y + i * lineHeight + fm.ascent)
        }
    }
}


