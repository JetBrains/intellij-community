// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotationClassIds
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableSymbol
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.InspectionLikeProcessingForElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

/**
 * Shared between K1 and K2 J2K.
 * Handles two cases:
 *   1. Local 'var' with initializer
 *   2. Private 'var' property
 *
 * To handle a local 'var' without an initializer, we need to use control-flow analysis to determine
 * if the variable has been definitely assigned. Currently, this is implemented only in K1 J2K with
 * [org.jetbrains.kotlin.idea.j2k.post.processing.processings.LocalVarToValInspectionBasedProcessing].
 */
class VarToValProcessing : InspectionLikeProcessingForElement<KtProperty>(KtProperty::class.java) {
    companion object {
        private val JPA_COLUMN_ANNOTATIONS: Set<ClassId> = setOf(
            ClassId.fromString("javax/persistence/Column"),
            ClassId.fromString("jakarta/persistence/Column"),
        )
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isApplicableTo(element: KtProperty, settings: ConverterSettings): Boolean {
        if (!element.isVar) return false

        return when {
            element.isLocal && element.initializer != null ->
                !element.hasWriteUsages(isLocal = true)

            element.isPrivate() -> allowAnalysisOnEdt {
                analyze(element) {
                    isApplicableToByAnalyze(element)
                }
            }

            else -> false
        }
    }

    context(KtAnalysisSession)
    private fun isApplicableToByAnalyze(element: KtProperty): Boolean {
        val symbol = element.getVariableSymbol() as? KtPropertySymbol ?: return false

        val overriddenSymbols = symbol.getAllOverriddenSymbols()
        if (overriddenSymbols.any { (it as? KtVariableSymbol)?.isVal == false }) return false

        val annotationClassIds = symbol.backingFieldSymbol?.annotationClassIds.orEmpty()
        if (annotationClassIds.any { JPA_COLUMN_ANNOTATIONS.contains(it) }) return false

        return !element.hasWriteUsages(isLocal = false)
    }

    private fun KtProperty.hasWriteUsages(isLocal: Boolean): Boolean {
        val usages = ReferencesSearch.search(this, useScope)
        return usages.any { usage ->
            val nameReference = (usage as? KtSimpleNameReference)?.element ?: return@any false

            // Skip usages of private property from 'init' block
            if (!isLocal) {
                val receiver = (nameReference.parent as? KtDotQualifiedExpression)?.receiverExpression
                if (nameReference.getStrictParentOfType<KtAnonymousInitializer>() != null
                    && (receiver == null || receiver is KtThisExpression)
                ) return@any false
            }

            nameReference.readWriteAccess(useResolveForReadWrite = true).isWrite
        }
    }

    override fun apply(element: KtProperty) {
        val psiFactory = KtPsiFactory(element.project)
        element.valOrVarKeyword.replace(psiFactory.createValKeyword())
    }
}