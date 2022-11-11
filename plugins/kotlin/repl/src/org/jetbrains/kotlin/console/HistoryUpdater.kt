// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.console.gutter.ConsoleIndicatorRenderer
import org.jetbrains.kotlin.console.gutter.ReplIcons

class HistoryUpdater(private val runner: KotlinConsoleRunner) {
    private val consoleView: LanguageConsoleImpl by lazy { runner.consoleView as LanguageConsoleImpl }

    fun printNewCommandInHistory(trimmedCommandText: String): TextRange {
        val historyEditor = consoleView.historyViewer
        addLineBreakIfNeeded(historyEditor)
        val startOffset = historyEditor.document.textLength

        addCommandTextToHistoryEditor(trimmedCommandText)
        val endOffset = historyEditor.document.textLength

        addFoldingRegion(historyEditor, startOffset, endOffset, trimmedCommandText)

        historyEditor.markupModel.addRangeHighlighter(
            startOffset, endOffset, HighlighterLayer.LAST, null, HighlighterTargetArea.EXACT_RANGE
        ).apply {
            val historyMarker = if (runner.isReadLineMode) ReplIcons.READLINE_MARKER else ReplIcons.COMMAND_MARKER
            gutterIconRenderer = ConsoleIndicatorRenderer(historyMarker)
        }

        historyEditor.scrollingModel.scrollVertically(endOffset)

        return TextRange(startOffset, endOffset)
    }

    private fun addCommandTextToHistoryEditor(trimmedCommandText: String) {
        val consoleEditor = consoleView.consoleEditor
        val consoleDocument = consoleEditor.document
        consoleDocument.setText(trimmedCommandText)
        LanguageConsoleImpl.printWithHighlighting(consoleView, consoleEditor, TextRange(0, consoleDocument.textLength))
        consoleView.flushDeferredText()
        consoleDocument.setText("")
    }

    private fun addLineBreakIfNeeded(historyEditor: EditorEx) {
        if (runner.isReadLineMode) return

        val historyDocument = historyEditor.document
        val historyText = historyDocument.text
        val textLength = historyText.length

        if (!historyText.endsWith('\n')) {
            historyDocument.insertString(textLength, "\n")

            if (textLength == 0) // this will work first time after 'Clear all' action
                runner.addGutterIndicator(historyEditor, ReplIcons.HISTORY_INDICATOR)
            else
                historyDocument.insertString(textLength + 1, "\n")

        } else if (!historyText.endsWith("\n\n")) {
            historyDocument.insertString(textLength, "\n")
        }
    }

    private fun addFoldingRegion(historyEditor: EditorEx, startOffset: Int, endOffset: Int, command: String) {
        val cmdLines = command.lines()
        val linesCount = cmdLines.size
        if (linesCount < 2) return

        val foldingModel = historyEditor.foldingModel
        foldingModel.runBatchFoldingOperation {
            foldingModel.addFoldRegion(startOffset, endOffset, "${cmdLines[0]} ...")
        }
    }
}