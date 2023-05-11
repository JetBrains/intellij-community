// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

interface KotlinMover : (KtNamedDeclaration, KtElement) -> KtNamedDeclaration {
    object Default : KotlinMover {
        override fun invoke(originalElement: KtNamedDeclaration, targetContainer: KtElement): KtNamedDeclaration {
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
                if (container is KtObjectDeclaration &&
                    container.isCompanion() &&
                    container.declarations.singleOrNull() == originalElement &&
                    KotlinMoveRefactoringSupport.getInstance()
                        .findReferencesToHighlight(container, LocalSearchScope(container.containingFile))
                        .isEmpty()
                ) {
                    container.deleteSingle()
                } else {
                    originalElement.deleteSingle()
                }
            }
        }
    }
}