// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

interface KotlinDeclarationMover {
    /**
     * Moves the [originalElement] from its original location into the given [targetContainer].
     * Implementations may work differently depending on the source or target and may
     * implement post-processing such as deleting empty source containers after moving.
     */
    fun moveDeclaration(originalElement: KtNamedDeclaration, targetContainer: KtElement): KtNamedDeclaration?

    /**
     * This mover moves the given declaration into its target.
     * If the source container is a companion object that is empty after moving, it is deleted after the move.
     */
    object Default : KotlinDeclarationMover {
        override fun moveDeclaration(originalElement: KtNamedDeclaration, targetContainer: KtElement): KtNamedDeclaration {
            @Suppress("DuplicatedCode") // this is a duplicate of the K1 counterpart, which remains for compatibility reasons
            return when (targetContainer) {
                is KtFile -> {
                    val declarationContainer: KtElement =
                        if (targetContainer.isScript()) targetContainer.script!!.blockExpression else targetContainer
                    declarationContainer.add(originalElement) as KtNamedDeclaration
                }

                is KtClassOrObject -> targetContainer.addDeclaration(originalElement)
                else -> throw KotlinExceptionWithAttachments("Unexpected element")
                    .withAttachment("context", targetContainer.getElementTextWithContext())
            }.apply {
                val container = originalElement.containingClassOrObject
                if (container is KtObjectDeclaration
                    && container.isCompanion()
                    && container.declarations.singleOrNull() == originalElement
                    && ReferencesSearch.search(container, LocalSearchScope(container.containingFile)).findAll().isEmpty()
                ) {
                    container.deleteSingle()
                } else {
                    originalElement.deleteSingle()
                }
            }
        }
    }
}