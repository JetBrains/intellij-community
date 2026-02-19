// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.inference.mutability

import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ClassReference
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintsCollectorAggregator
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ContextCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.InferenceFacade
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ResolveSuperFunctionsProvider
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.CallExpressionConstraintCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.CommonConstraintsCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.FunctionConstraintsCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.descriptor
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability.MutabilityBoundTypeCalculator
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability.MutabilityBoundTypeEnhancer
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability.MutabilityConstraintBoundProvider
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability.MutabilityConstraintsCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability.MutabilityDefaultStateProvider
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability.MutabilityStateUpdater
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.j2k.J2K_PROJECT_DESCRIPTOR
import org.jetbrains.kotlin.nj2k.inference.AbstractConstraintCollectorTest
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

abstract class AbstractMutabilityInferenceTest : AbstractConstraintCollectorTest() {
    override fun createInferenceFacade(resolutionFacade: ResolutionFacade): InferenceFacade {
        val typeEnhancer = MutabilityBoundTypeEnhancer()
        return InferenceFacade(
            object : ContextCollector(resolutionFacade) {
                override fun ClassReference.getState(typeElement: KtTypeElement?) =
                    when (descriptor?.fqNameOrNull()) {
                        in MutabilityStateUpdater.mutableToImmutable -> State.UNKNOWN
                        in MutabilityStateUpdater.immutableToMutable -> State.UNKNOWN
                        else -> State.UNUSED
                    }
            },
            ConstraintsCollectorAggregator(
                resolutionFacade,
                MutabilityConstraintBoundProvider(),
                listOf(
                    CommonConstraintsCollector(),
                    CallExpressionConstraintCollector(),
                    FunctionConstraintsCollector(ResolveSuperFunctionsProvider(resolutionFacade)),
                    MutabilityConstraintsCollector()
                )
            ),
            MutabilityBoundTypeCalculator(resolutionFacade, typeEnhancer),
            MutabilityStateUpdater(),
            MutabilityDefaultStateProvider(),
            renderDebugTypes = true
        )
    }

    override fun KtFile.afterInference(): Unit = runWriteAction {
        commitAndUnblockDocument()
        ShortenReferences.DEFAULT.process(this)
    }

    override fun KtFile.prepareFile() = runWriteAction {
        fun KtTypeReference.updateMutability() {
            MutabilityStateUpdater.changeState(
                typeElement ?: return,
                analyze()[BindingContext.TYPE, this]!!,
                toMutable = true
            )
            for (typeArgument in typeElement!!.typeArgumentsAsTypes) {
                typeArgument.updateMutability()
            }
        }
        for (typeReference in collectDescendantsOfType<KtTypeReference>()) {
            if (typeReference.parent is KtConstructorCalleeExpression) continue
            typeReference.updateMutability()
        }
        deleteComments()
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = J2K_PROJECT_DESCRIPTOR
}