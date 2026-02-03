// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ui

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ui.KotlinFirExtractFunctionDialog.createNewDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.Generator
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.validate
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeCodeFragment
import org.jetbrains.kotlin.types.Variance

@OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
internal fun render(type: KaType, context: KtElement): String {
    return allowAnalysisOnEdt {
        analyze(context) {
            type.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.IN_VARIANCE)
        }
    }
}

@OptIn(KaAllowAnalysisOnEdt::class)
internal fun getKtType(fragment: KtTypeCodeFragment): KaType? {
    return allowAnalysisOnEdt {
        analyze(fragment) {
            fragment.getContentElement()?.type
        }
    }
}

internal fun validate(
    originalDescriptor: ExtractableCodeDescriptor,
    newName: String,
    newVisibility: KtModifierKeywordToken?,
    newReceiverInfo: FirExtractFunctionParameterTablePanel.ParameterInfo?,
    newParameterInfos: List<FirExtractFunctionParameterTablePanel.ParameterInfo>,
    returnCodeFragment: KtTypeCodeFragment,
    context: KtElement
): ExtractableCodeDescriptorWithConflicts {
    return analyzeInModalWindow(context, KotlinBundle.message("fix.change.signature.prepare")) {
        val newDescriptor = createNewDescriptor(
            originalDescriptor,
            newName,
            newVisibility,
            newReceiverInfo,
            newParameterInfos,
            returnCodeFragment.getContentElement()?.type
        )

        newDescriptor.validate()
    }

}

internal fun getSignaturePreview(
    originalDescriptor: ExtractableCodeDescriptor,
    newName: String,
    newVisibility: KtModifierKeywordToken?,
    newReceiverInfo: FirExtractFunctionParameterTablePanel.ParameterInfo?,
    newParameterInfos: List<FirExtractFunctionParameterTablePanel.ParameterInfo>,
    returnCodeFragment: KtTypeCodeFragment,
    context: KtElement
): String {
    return analyze(context) {
        val newDescriptor = createNewDescriptor(
            originalDescriptor,
            newName,
            newVisibility,
            newReceiverInfo,
            newParameterInfos,
            returnCodeFragment.getContentElement()?.type
        )

        Generator.getSignaturePreview(ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT))
    }
}
