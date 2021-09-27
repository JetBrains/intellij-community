// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.resolveCandidates
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.util.getCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.resolveToDeclarationImpl

class IdeaKotlinUastResolveProviderService : KotlinUastResolveProviderService {
    override fun getBindingContext(element: KtElement) = element.analyze(BodyResolveMode.PARTIAL_WITH_CFA)

    override fun isJvmElement(psiElement: PsiElement): Boolean = psiElement.isJvmElement

    override fun getLanguageVersionSettings(element: KtElement): LanguageVersionSettings {
        return element.languageVersionSettings
    }

    override fun getReferenceVariants(ktExpression: KtExpression, nameHint: String): Sequence<PsiElement> {
        val resolutionFacade = ktExpression.getResolutionFacade()
        val bindingContext = ktExpression.analyze()
        val call = ktExpression.getCall(bindingContext) ?: return emptySequence()
        return call.resolveCandidates(bindingContext, resolutionFacade)
            .mapNotNull { resolveToDeclarationImpl(ktExpression, it.candidateDescriptor) }
            .asSequence()
    }
}
