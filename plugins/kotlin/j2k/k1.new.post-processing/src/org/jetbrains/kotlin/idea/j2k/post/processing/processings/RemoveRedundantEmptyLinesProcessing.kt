// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.j2k.post.processing.ElementsBasedPostProcessing
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Removes empty lines after left brace and before right brace of code blocks.
 *
 * It's easier to remove them all at once in a dedicated processing,
 * because such lines may be introduced rather randomly from various other processings.
 */
internal class RemoveRedundantEmptyLinesProcessing : ElementsBasedPostProcessing() {
    override fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext) {
        val containers = runReadAction {
            elements.descendantsOfType<KtBlockExpression>() +
                    elements.descendantsOfType<KtClassBody>() +
                    elements.descendantsOfType<KtFunctionLiteral>()
        }

        if (containers.isEmpty()) return
        val factory = runReadAction { KtPsiFactory(containers.first().project) }

        for (container in containers) {
            val (firstWhitespace, lastWhitespace) = runReadAction {
                container.firstChild?.nextSibling as? PsiWhiteSpace to container.lastChild?.prevSibling as? PsiWhiteSpace
            }
            firstWhitespace?.removeRedundantEmptyLines(factory)
            lastWhitespace?.removeRedundantEmptyLines(factory)
        }
    }

    private fun PsiWhiteSpace.removeRedundantEmptyLines(factory: KtPsiFactory) {
        if (StringUtil.getLineBreakCount(text) > 1) {
            runUndoTransparentActionInEdt(inWriteAction = true) {
                this.replace(factory.createNewLine())
            }
        }
    }
}