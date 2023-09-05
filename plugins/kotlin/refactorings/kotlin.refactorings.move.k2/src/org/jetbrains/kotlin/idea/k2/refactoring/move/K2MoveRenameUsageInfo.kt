// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.util.MoveRenameUsageInfo
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

sealed class K2MoveRenameUsageInfo(
    element: KtElement,
    reference: KtReference,
    referencedElement: KtNamedDeclaration
) : MoveRenameUsageInfo(element, reference, referencedElement) {
    val usageElement: KtElement get() = element as KtElement

    val referencedElement: KtNamedDeclaration get() = getReferencedElement() as KtNamedDeclaration

    abstract fun retarget(to: KtNamedDeclaration)

    /**
     * A usage that can be represented in qualified form like for example a type reference.
     */
    class Qualifiable(
        element: KtElement,
        reference: KtReference,
        referencedElement: KtNamedDeclaration
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override fun retarget(to: KtNamedDeclaration) {
            reference?.bindToElement(to)
        }
    }

    /**
     * A usage that can't be represented in qualified form like for example a call to an extension function.
     */
    class Unqualifiable(
        element: KtElement,
        reference: KtReference,
        referencedElement: KtNamedDeclaration
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override fun retarget(to: KtNamedDeclaration) {
            to.fqName?.let(usageElement.containingKtFile::addImport)
        }
    }

    companion object {
        @OptIn(KtAllowAnalysisFromWriteAction::class)
        fun find(declaration: KtNamedDeclaration): List<MoveRenameUsageInfo> {
            fun KtSimpleNameExpression.isExtensionReference(): Boolean = allowAnalysisFromWriteAction {
                analyze(this) {
                    resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.extensionReceiver != null
                }
            }

            return ReferencesSearch.search(declaration).findAll().map { ref ->
                if (ref is KtReference) {
                    val usageElem = ref.element
                    if (usageElem is KtSimpleNameExpression && usageElem.isExtensionReference()) {
                        Unqualifiable(usageElem, ref, declaration)
                    } else {
                        Qualifiable(usageElem, ref, declaration)
                    }
                } else {
                    MoveRenameUsageInfo(ref.element, ref, declaration)
                }
            }
        }
    }
}