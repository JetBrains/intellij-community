// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.isKotlinScratch
import org.jetbrains.kotlin.idea.jvm.shared.scratch.isKotlinWorksheet
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.KtScratchFileEditorProvider

internal  class K1ScratchFileEditorProvider() : KtScratchFileEditorProvider() {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid) {
            return false
        }
        if (!file.isKotlinScratch && !file.isKotlinWorksheet) {
            return false
        }
        val psiFile =
          ApplicationManager.getApplication().runReadAction(Computable { PsiManager.getInstance(project).findFile(file) }) ?: return false
        return ScratchFileLanguageProvider.get(psiFile.fileType) != null
    }

    override suspend fun createFileEditor(
      project: Project, file: VirtualFile, document: Document?, editorCoroutineScope: CoroutineScope
    ): FileEditor {
        val textEditorProvider = TextEditorProvider.Companion.getInstance()
        val scratchFile = readAction { createScratchFile(project, file) } ?: return textEditorProvider.createFileEditor(
            project = project,
            file = file,
            document = document,
            editorCoroutineScope = editorCoroutineScope,
        )

        val mainEditor = textEditorProvider.createFileEditor(
          project = project,
          file = scratchFile.file,
          document = readAction { FileDocumentManager.getInstance().getDocument(scratchFile.file) },
          editorCoroutineScope = editorCoroutineScope,
        )
        val editorFactory = serviceAsync<EditorFactory>()
        return withContext(Dispatchers.EDT) {
          val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
          Disposer.register(mainEditor, Disposable { editorFactory.releaseEditor(viewer) })
          val previewEditor = textEditorProvider.getTextEditor(viewer)
            K1ScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val scratchFile = runBlockingCancellable {
          createScratchFile(project, file)
        } ?: return TextEditorProvider.Companion.getInstance().createEditor(project, file)
        return K1ScratchFileEditorWithPreview.Companion.create(scratchFile)
    }

    private fun createScratchFile(project: Project, file: VirtualFile): K1KotlinScratchFile? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val scratchFile =
            ScratchFileLanguageProvider.get(psiFile.language)?.newScratchFile(project, file) as? K1KotlinScratchFile ?: return null
        setupCodeAnalyzerRestarterOutputHandler(project, scratchFile)

        return scratchFile
    }

    private fun setupCodeAnalyzerRestarterOutputHandler(project: Project, scratchFile: K1KotlinScratchFile) {
        scratchFile.replScratchExecutor?.addOutputHandler(object : ScratchOutputHandlerAdapter() {
            override fun onFinish(file: ScratchFile) {
                ApplicationManager.getApplication().invokeLater {
                    if (!file.project.isDisposed) {
                        val scratch = file.getPsiFile()
                        if (scratch?.isValid == true) {
                            DaemonCodeAnalyzer.getInstance(project).restart(scratch)
                        }
                    }
                }
            }
        })
    }
}