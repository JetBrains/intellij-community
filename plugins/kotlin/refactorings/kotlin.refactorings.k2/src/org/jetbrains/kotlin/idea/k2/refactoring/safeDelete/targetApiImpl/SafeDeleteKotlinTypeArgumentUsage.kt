// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.targetApiImpl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.rename.api.FileOperation
import com.intellij.refactoring.safeDelete.api.FileUpdater
import com.intellij.refactoring.safeDelete.api.PsiSafeDeleteUsage
import com.intellij.util.text.StringOperation
import org.jetbrains.kotlin.lexer.KtTokens


class SafeDeleteKotlinTypeArgumentUsage(
    private val psiElement : PsiElement
) : PsiSafeDeleteUsage {
    private val psiUsage = PsiUsage.textUsage(psiElement, TextRange.create(0, psiElement.textLength))

    override fun createPointer(): Pointer<out PsiSafeDeleteUsage> {
        return Pointer.delegatingPointer(psiUsage.createPointer()) {
            SafeDeleteKotlinTypeArgumentUsage(psiElement)
        }
    }

    override val file: PsiFile = psiUsage.file
    override val range: TextRange = psiUsage.range
    override var conflictMessage: String? = null
    override var isSafeToDelete: Boolean = true
    override val fileUpdater: FileUpdater
        get() = object : FileUpdater {
            override fun prepareFileUpdate(): Collection<FileOperation> {
                var additionalRange : TextRange? = null
                var comma = PsiTreeUtil.skipWhitespacesAndCommentsForward(psiElement)
                if (comma != null && comma.elementType == KtTokens.COMMA) {
                    additionalRange = TextRange.create(psiElement.textRange.endOffset, comma.textRange.endOffset)
                }
                else {
                    comma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(psiElement)

                    if (comma != null && comma.elementType == KtTokens.COMMA) {
                        additionalRange = TextRange.create(comma.textRange.startOffset, psiElement.textRange.startOffset)
                    }
                }

                if (additionalRange == null) return listOf(FileOperation.modifyFile(file, StringOperation.remove(range)))
                return listOf(FileOperation.modifyFile(file, StringOperation.remove(range), StringOperation.remove(additionalRange)))
            }
        }
}

