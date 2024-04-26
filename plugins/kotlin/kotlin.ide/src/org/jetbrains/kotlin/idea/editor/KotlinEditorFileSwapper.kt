// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorFileSwapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtDecompiledFile
import org.jetbrains.kotlin.psi.KotlinDeclarationNavigationPolicy
import org.jetbrains.kotlin.psi.KtDeclaration

class KotlinEditorFileSwapper : EditorFileSwapper {

    override fun getFileToSwapTo(project: Project, composite: EditorComposite): Pair<VirtualFile, Int?>? {
        val file = composite.file
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        if (psiFile !is KtDecompiledFile) {
            return null
        }
        val location = getSourcesLocation(psiFile) ?: return null
        val oldEditor = EditorFileSwapper.findSinglePsiAwareEditor(composite.allEditors)
        return if (oldEditor != null) {
            var offset = oldEditor.getEditor().getCaretModel().offset
            offset = getCursorPosition(offset, psiFile)
            Pair(location, offset)
        } else {
            Pair(location, 0)
        }
    }

    private fun getSourcesLocation(psiFile: KtDecompiledFile): VirtualFile? = psiFile.declarations
        .firstOrNull()?.let {
            val element = serviceOrNull<KotlinDeclarationNavigationPolicy>()?.getNavigationElement(it) ?: return@let null
            // declaration from the decompiled file should not be equal to a source declaration
            if (it != element) {
                val sourceFile = element.containingFile
                if (sourceFile.isValid) {
                    return sourceFile.virtualFile
                }
            }
            return null
        }

    private fun getCursorPosition(originalOffset: Int, decompiledFile: KtDecompiledFile): Int {
        val cursor = decompiledFile.navigationElement.findElementAt(originalOffset) ?: return 0
        val declarationInSources = PsiTreeUtil.getParentOfType(cursor, KtDeclaration::class.java, false)?.let {
            serviceOrNull<KotlinDeclarationNavigationPolicy>()?.getNavigationElement(it)
        }
        return declarationInSources?.textOffset ?: 0
    }
}