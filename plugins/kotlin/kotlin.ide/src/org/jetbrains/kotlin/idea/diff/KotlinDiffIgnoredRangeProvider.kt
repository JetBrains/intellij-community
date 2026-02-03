// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.diff

import com.intellij.diff.lang.LangDiffIgnoredRangeProvider
import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtImportList

class KotlinDiffIgnoredRangeProvider : LangDiffIgnoredRangeProvider() {
    override fun getDescription(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
        return KotlinBundle.message("ignore.imports.and.formatting")
    }

    override fun accepts(project: Project, language: Language): Boolean {
        return KotlinLanguage.INSTANCE.equals(language)
    }

    override fun computeIgnoredRanges(
        project: Project,
        text: CharSequence,
        language: Language
    ): List<TextRange> {
        return ReadAction.nonBlocking<List<TextRange>> {
            val result = mutableListOf<TextRange>()
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText("", language, text)

            psiFile.accept(object : PsiElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (element.getTextLength() == 0) return

                    if (isIgnored(element)) {
                        result.add(element.getTextRange())
                    } else {
                        element.acceptChildren(this)
                    }
                }
            })
            result
        }.executeSynchronously()
    }

    private fun isIgnored(element: PsiElement): Boolean {
        if (element is PsiWhiteSpace) return true
        if (element is KtImportList) return true
        return false
    }
}