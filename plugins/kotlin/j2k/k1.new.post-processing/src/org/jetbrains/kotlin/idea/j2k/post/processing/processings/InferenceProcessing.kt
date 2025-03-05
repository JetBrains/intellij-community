// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.BoundTypeCalculatorImpl
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ByInfoSuperFunctionsProvider
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintsCollectorAggregator
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.InferenceFacade
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.CallExpressionConstraintCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.CommonConstraintsCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.FunctionConstraintsCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability.*
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.nullability.*
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.ElementsBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.psi.KtElement

internal abstract class InferenceProcessing : ElementsBasedPostProcessing() {
    override fun runProcessing(elements: List<PsiElement>, converterContext: ConverterContext) {
        val kotlinElements = elements.filterIsInstance<KtElement>()
        if (kotlinElements.isEmpty()) return
        val resolutionFacade = runReadAction {
            KotlinCacheService.getInstance(converterContext.project).getResolutionFacade(kotlinElements)
        }
        createInferenceFacade(resolutionFacade, converterContext).runOn(kotlinElements)
    }

    abstract fun createInferenceFacade(
        resolutionFacade: ResolutionFacade,
        converterContext: ConverterContext
    ): InferenceFacade

    override fun computeApplier(elements: List<PsiElement>, converterContext: ConverterContext): PostProcessingApplier {
        error("Not supported in K1 J2K")
    }
}

internal class NullabilityInferenceProcessing : InferenceProcessing() {
    override fun createInferenceFacade(
        resolutionFacade: ResolutionFacade,
        converterContext: ConverterContext
    ): InferenceFacade = InferenceFacade(
        NullabilityContextCollector(resolutionFacade, converterContext),
        ConstraintsCollectorAggregator(
            resolutionFacade,
            NullabilityConstraintBoundProvider(),
            listOf(
                CommonConstraintsCollector(),
                CallExpressionConstraintCollector(),
                FunctionConstraintsCollector(ByInfoSuperFunctionsProvider(resolutionFacade, converterContext)),
                NullabilityConstraintsCollector()
            )
        ),
        BoundTypeCalculatorImpl(resolutionFacade, NullabilityBoundTypeEnhancer(resolutionFacade)),
        NullabilityStateUpdater(),
        NullabilityDefaultStateProvider()
    )
}

internal class MutabilityInferenceProcessing : InferenceProcessing() {
    override fun createInferenceFacade(
        resolutionFacade: ResolutionFacade,
        converterContext: ConverterContext
    ): InferenceFacade = InferenceFacade(
        MutabilityContextCollector(resolutionFacade, converterContext),
        ConstraintsCollectorAggregator(
            resolutionFacade,
            MutabilityConstraintBoundProvider(),
            listOf(
                CommonConstraintsCollector(),
                CallExpressionConstraintCollector(),
                FunctionConstraintsCollector(ByInfoSuperFunctionsProvider(resolutionFacade, converterContext)),
                MutabilityConstraintsCollector()
            )
        ),
        MutabilityBoundTypeCalculator(resolutionFacade, MutabilityBoundTypeEnhancer()),
        MutabilityStateUpdater(),
        MutabilityDefaultStateProvider()
    )
}