// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.ElementsBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.nj2k.descendantsOfType
import org.jetbrains.kotlin.nj2k.runUndoTransparentActionInEdt
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
class RemoveRedundantEmptyLinesProcessing : ElementsBasedPostProcessing() {
    override fun runProcessing(elements: List<PsiElement>, converterContext: ConverterContext) {
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

    override fun computeApplier(elements: List<PsiElement>, converterContext: ConverterContext): PostProcessingApplier {
        val containers = elements.descendantsOfType<KtBlockExpression>() +
                elements.descendantsOfType<KtClassBody>() +
                elements.descendantsOfType<KtFunctionLiteral>()

        if (containers.isEmpty()) return Applier.EMPTY

        val elementPointers = mutableListOf<SmartPsiElementPointer<PsiWhiteSpace>>()
        val factory = KtPsiFactory(containers.first().project)

        for (container in containers) {
            val whiteSpaces = listOf(container.firstChild?.nextSibling, container.lastChild?.prevSibling)
                .filterIsInstance<PsiWhiteSpace>()

            for (whiteSpace in whiteSpaces) {
                if (StringUtil.getLineBreakCount(whiteSpace.text) > 1) {
                    elementPointers += whiteSpace.createSmartPointer()
                }
            }
        }

        return Applier(elementPointers, factory)
    }

    private class Applier(
        private val elementPointers: List<SmartPsiElementPointer<PsiWhiteSpace>>,
        private val factory: KtPsiFactory?,
    ) : PostProcessingApplier {
        override fun apply() {
            if (factory == null) return
            for (pointer in elementPointers) {
                val whiteSpace = pointer.element ?: continue
                whiteSpace.replace(factory.createNewLine())
            }
        }

        companion object {
            val EMPTY = Applier(emptyList(), factory = null)
        }
    }
}