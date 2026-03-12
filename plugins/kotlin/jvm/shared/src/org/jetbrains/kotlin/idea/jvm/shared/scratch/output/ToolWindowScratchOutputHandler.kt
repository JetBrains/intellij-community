// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.output

import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Implements logic of shared pointer for the toolWindow output handler.
 *
 * Not thread safe! Can be used only from the EDT.
 */
object ScratchToolWindowHandlerKeeper {
    private var toolWindowHandler: ScratchOutputHandler? = null
    private var toolWindowDisposable = Disposer.newDisposable()
    private var counter = 0

    fun requestOutputHandler(): ScratchOutputHandler {
        if (counter == 0) {
            toolWindowHandler = ToolWindowScratchOutputHandler(toolWindowDisposable)
        }

        counter += 1
        return toolWindowHandler!!
    }

    fun releaseOutputHandler(scratchOutputHandler: ScratchOutputHandler) {
        require(counter > 0) { "Counter is $counter, nothing to release!" }
        require(toolWindowHandler === scratchOutputHandler) { "$scratchOutputHandler differs from stored $toolWindowHandler" }

        counter -= 1
        if (counter == 0) {
            Disposer.dispose(toolWindowDisposable)
            toolWindowDisposable = Disposer.newDisposable()
            toolWindowHandler = null
        }
    }
}

private class ToolWindowScratchOutputHandler(private val parentDisposable: Disposable) : ScratchOutputHandlerAdapter() {

    override fun handle(file: ScratchFile, output: ScratchOutput) {
        printToConsole(file) {
            print(output.text, output.type.convert())
        }
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        printToConsole(file) {
            val psiFile = file.getPsiFile()
            if (psiFile != null) {
                printHyperlink(
                    getLineInfo(psiFile, expression),
                    OpenFileHyperlinkInfo(
                        project,
                        psiFile.virtualFile,
                        expression.lineStart
                    )
                )
                print(" ", ConsoleViewContentType.NORMAL_OUTPUT)
            }
            print(output.text, output.type.convert())
        }
    }

    override fun error(file: ScratchFile, message: String) {
        printToConsole(file) {
            print(message, ConsoleViewContentType.ERROR_OUTPUT)
        }
    }

    private fun printToConsole(file: ScratchFile, print: ConsoleViewImpl.() -> Unit) {
        invokeLater {
            val project = file.project.takeIf { !it.isDisposed } ?: return@invokeLater

            val toolWindow = getToolWindow(project) ?: createToolWindow(file)

            val contents = toolWindow.contentManager.contents
            for (content in contents) {
                val component = content.component
                if (component is ConsoleViewImpl) {
                    component.clear()
                    component.print()
                    component.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
                }
            }

            toolWindow.setAvailable(true, null)

            if (!file.options.isInteractiveMode) {
                toolWindow.show(null)
            }

            toolWindow.setIcon(ExecutionUtil.getLiveIndicator(AllIcons.FileTypes.Text))
        }
    }

    override fun clear(file: ScratchFile) {
        invokeLater {
            val toolWindow = getToolWindow(file.project) ?: return@invokeLater
            val contents = toolWindow.contentManager.contents
            for (content in contents) {
                val component = content.component
                if (component is ConsoleViewImpl) {
                    component.clear()
                }
            }

            if (!file.options.isInteractiveMode) {
                toolWindow.hide(null)
            }

            toolWindow.setIcon(AllIcons.FileTypes.Text)
        }
    }

    private fun ScratchOutputType.convert() = when (this) {
        ScratchOutputType.OUTPUT -> ConsoleViewContentType.SYSTEM_OUTPUT
        ScratchOutputType.RESULT -> ConsoleViewContentType.NORMAL_OUTPUT
        ScratchOutputType.ERROR -> ConsoleViewContentType.ERROR_OUTPUT
    }

    private fun getToolWindow(project: Project): ToolWindow? {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        return toolWindowManager.getToolWindow(ScratchToolWindowFactory.ID)
    }

    private fun createToolWindow(file: ScratchFile): ToolWindow {
        val project = file.project
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.registerToolWindow(ScratchToolWindowFactory.ID, true, ToolWindowAnchor.BOTTOM)
        val window =
            toolWindowManager.getToolWindow(ScratchToolWindowFactory.ID) ?: error("ScratchToolWindowFactory.ID should be registered")
        ScratchToolWindowFactory().createToolWindowContent(project, window)

        Disposer.register(parentDisposable, Disposable {
            toolWindowManager.unregisterToolWindow(ScratchToolWindowFactory.ID)
        })

        return window
    }
}

fun getLineInfo(psiFile: PsiFile, expression: ScratchExpression): String =
    "${psiFile.name}:${expression.lineStart + 1}"

class ScratchToolWindowFactory : ToolWindowFactory {
    companion object {
        const val ID = "Scratch Output"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val consoleView = ConsoleViewImpl(project, true)
        toolWindow.setToHideOnEmptyContent(true)
        toolWindow.setIcon(AllIcons.FileTypes.Text)
        toolWindow.hide(null)

        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(consoleView.component, null, false)
        contentManager.addContent(content)
        val editor = consoleView.editor
        if (editor is EditorEx) {
            editor.isRendererMode = true
        }

        Disposer.register(KotlinPluginDisposable.getInstance(project), consoleView)
    }
}
