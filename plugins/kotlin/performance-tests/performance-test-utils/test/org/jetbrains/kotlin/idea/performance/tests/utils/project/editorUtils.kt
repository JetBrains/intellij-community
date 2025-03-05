// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.performance.tests.utils.project

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.performance.tests.utils.projectFileByName

fun openInEditor(project: Project, name: String): EditorFile {
    val psiFile = projectFileByName(project, name)
    return openInEditor(project, psiFile.virtualFile)
}

fun openInEditor(project: Project, vFile: VirtualFile): EditorFile {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val fileEditorManager = FileEditorManager.getInstance(project)

    val psiFile = vFile.toPsiFile(project) ?: error("Unable to find psi file for ${vFile.path}")

    TestCase.assertTrue("file $vFile is not indexed yet", FileIndexFacade.getInstance(project).isInContent(vFile))

    fileEditorManager.openFile(vFile, true)
    val document = fileDocumentManager.getDocument(vFile) ?: error("no document for $vFile found")

    TestCase.assertNotNull("doc not found for $vFile", EditorFactory.getInstance().getEditors(document))
    TestCase.assertTrue("expected non empty doc", document.text.isNotEmpty())

    val offset = psiFile.textOffset
    TestCase.assertTrue("side effect: to load the text", offset >= 0)

    return EditorFile(psiFile = psiFile, document = document)
}

data class EditorFile(val psiFile: PsiFile, val document: Document)
