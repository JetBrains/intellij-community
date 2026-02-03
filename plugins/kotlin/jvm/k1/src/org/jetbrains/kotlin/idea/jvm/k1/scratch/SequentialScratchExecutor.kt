// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExpression
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@K1Deprecation
abstract class SequentialScratchExecutor(override val scratchFile: K1KotlinScratchFile) : ScratchExecutor(scratchFile) {
    abstract fun executeStatement(expression: ScratchExpression)

    protected abstract fun startExecution()
    protected abstract fun stopExecution(callback: (() -> Unit)? = null)

    protected abstract fun needProcessToStart(): Boolean

    fun start() {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, scratchFile.project.messageBus.connect())

        startExecution()
    }

    override fun stop() {
        EditorFactory.getInstance().eventMulticaster.removeDocumentListener(listener)

        stopExecution()
    }

    fun executeNew() {
        val expressions = scratchFile.getExpressions()
        if (wasExpressionExecuted(expressions.size)) return

        handler.onStart(scratchFile)

        for ((index, expression) in expressions.withIndex()) {
            if (wasExpressionExecuted(index)) continue

            executeStatement(expression)
            lastExecuted = index
        }
    }

    override fun execute() {
        if (needToRestartProcess()) {
            resetLastExecutedIndex()
            handler.clear(scratchFile)

            handler.onStart(scratchFile)
            stopExecution {
                ApplicationManager.getApplication().invokeLater {
                    executeNew()
                }
            }
        } else {
            executeNew()
        }
    }

    fun getFirstNewExpression(): ScratchExpression? {
        val expressions = runReadAction { scratchFile.getExpressions() }
        val firstNewExpressionIndex = lastExecuted + 1
        if (firstNewExpressionIndex in expressions.indices) {
            return expressions[firstNewExpressionIndex]
        }
        return null
    }

    private val listener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (event.newFragment.isBlank() && event.oldFragment.isBlank()) return
            if (!needToRestartProcess()) return

            val document = event.document
            val virtualFile = FileDocumentManager.getInstance().getFile(document)?.takeIf { it.isInLocalFileSystem } ?: return
            if (!virtualFile.isValid) {
                return
            }

            if (PsiManager.getInstance(scratchFile.project).findFile(virtualFile) != scratchFile.getPsiFile()) return

            val changedLine = document.getLineNumber(event.offset)
            val changedExpression = scratchFile.getExpressionAtLine(changedLine) ?: return
            val changedExpressionIndex = scratchFile.getExpressions().indexOf(changedExpression)
            if (wasExpressionExecuted(changedExpressionIndex)) {
                resetLastExecutedIndex()
                handler.clear(scratchFile)

                stopExecution()
            }
        }
    }

    private var lastExecuted = -1

    private fun needToRestartProcess(): Boolean {
        return lastExecuted > -1
    }

    private fun resetLastExecutedIndex() {
        lastExecuted = -1
    }

    private fun wasExpressionExecuted(index: Int): Boolean {
        return index <= lastExecuted
    }

    @TestOnly
    fun stopAndWait() {
        val lock = Semaphore(1)
        lock.acquire()
        stopExecution {
            lock.release()
        } // blocking UI thread!?
        check(lock.tryAcquire(2, TimeUnit.SECONDS)) {
            "Couldn't stop REPL process in 2 seconds"
        }
    }
}