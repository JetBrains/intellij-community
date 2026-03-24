// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutput
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandler
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchToolWindowHandlerKeeper
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.getLineInfo
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtPsiFactory

@K1Deprecation
class K1ScratchFileEditorWithPreview(
  val kotlinScratchFile: K1KotlinScratchFile, sourceTextEditor: TextEditor, previewTextEditor: TextEditor
) : ScratchFileEditorWithPreview(kotlinScratchFile, sourceTextEditor, previewTextEditor) {
    init {
        kotlinScratchFile.compilingScratchExecutor?.addOutputHandler(commonPreviewOutputHandler)
        kotlinScratchFile.replScratchExecutor?.addOutputHandler(commonPreviewOutputHandler)
    }

    override fun dispose() {
        kotlinScratchFile.replScratchExecutor?.stop()
        kotlinScratchFile.compilingScratchExecutor?.stop()
        super.dispose()
    }

    override fun createToolbar(): ActionToolbar = ScratchTopPanel(kotlinScratchFile).actionsToolbar

    override fun requestOutputHandler(): ScratchOutputHandler {
        return if (isUnitTestMode()) {
            TestOutputHandler
        } else {
            ScratchToolWindowHandlerKeeper.requestOutputHandler()
        }
    }

    companion object {
        fun create(scratchFile: K1KotlinScratchFile): ScratchFileEditorWithPreview {
            val textEditorProvider = TextEditorProvider.getInstance()

            val mainEditor = textEditorProvider.createEditor(scratchFile.project, scratchFile.virtualFile) as TextEditor
            val editorFactory = EditorFactory.getInstance()

            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor, Disposable { editorFactory.releaseEditor(viewer) })

            val previewEditor = textEditorProvider.getTextEditor(viewer)

            return K1ScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }
}

private object TestOutputHandler : ScratchOutputHandlerAdapter() {
    private val errors = arrayListOf<String>()
    private val inlays = arrayListOf<Pair<ScratchExpression, String>>()

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        inlays.add(expression to output.text)
    }

    override fun error(file: ScratchFile, message: String) {
        errors.add(message)
    }

    override fun onFinish(file: ScratchFile) {
        TransactionGuard.submitTransaction(KotlinPluginDisposable.getInstance(file.project), Runnable {
            val psiFile = file.getPsiFile()
                ?: error(
                    "PsiFile cannot be found for scratch to render inlays in tests:\n" +
                            "project.isDisposed = ${file.project.isDisposed}\n" +
                            "inlays = ${inlays.joinToString { it.second }}\n" +
                            "errors = ${errors.joinToString()}"
                )

            if (inlays.isNotEmpty()) {
                testPrint(psiFile, inlays.map { (expression, text) ->
                    "/** ${getLineInfo(psiFile, expression)} $text */"
                })
                inlays.clear()
            }

            if (errors.isNotEmpty()) {
                testPrint(psiFile, listOf(errors.joinToString(prefix = "/** ", postfix = " */")))
                errors.clear()
            }
        })
    }

    private fun testPrint(file: PsiFile, comments: List<String>) {
        WriteCommandAction.runWriteCommandAction(file.project) {
            for (comment in comments) {
                file.addAfter(
                    KtPsiFactory(file.project).createComment(comment),
                    file.lastChild
                )
            }
        }
    }
}