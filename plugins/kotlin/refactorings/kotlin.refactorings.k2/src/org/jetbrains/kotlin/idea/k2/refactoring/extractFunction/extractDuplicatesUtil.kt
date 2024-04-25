// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.matchRanges
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionDataAnalyzer
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.DuplicateInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.getControlFlowIfMatched
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.getOccurrenceContainer
import org.jetbrains.kotlin.idea.refactoring.introduce.getPhysicalTextRange
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression


fun ExtractableCodeDescriptor.findDuplicates(): List<DuplicateInfo<KtType>> {
    val scopeElement = getOccurrenceContainer() as? KtElement ?: return emptyList()
    val originalTextRange = extractionData.originalRange.getPhysicalTextRange()
    return analyze(scopeElement) {
        extractionData.originalRange.match(scopeElement) { targetRange, patternRange ->
            matchRanges(targetRange, patternRange, parameters.map { it.originalDescriptor })
        }.filter { !(it.range.getPhysicalTextRange().intersects(originalTextRange)) }
            .mapNotNull { match ->
                val data = extractionData.copy(originalRange = match.range)
                val analysisResult = ExtractionDataAnalyzer(data).performAnalysis()
                val controlFlow = getControlFlowIfMatched(match, analysisResult)
                val range = with(match.range) {
                    (elements.singleOrNull() as? KtStringTemplateEntryWithExpression)?.expression?.toRange() ?: this
                }

                controlFlow?.let {
                    DuplicateInfo(range, it, parameters.map { param ->
                        match.substitution.getValue(param.originalDescriptor).text!!
                    })
                }
            }
            .toList()
    }
}
