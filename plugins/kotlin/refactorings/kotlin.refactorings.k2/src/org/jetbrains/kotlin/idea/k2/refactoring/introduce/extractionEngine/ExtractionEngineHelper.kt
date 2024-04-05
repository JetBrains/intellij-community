// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionData
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionEngineHelper

abstract class ExtractionEngineHelper(@NlsContexts.DialogTitle operationName: String) :
    IExtractionEngineHelper<KtType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts>(
        operationName
    ) {


    // called under potemkin progress, still in EDT but the progress is visible
    @OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
    override fun generateDeclaration(config: ExtractionGeneratorConfiguration): ExtractionResult {
        return allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                Generator.generateDeclaration(config, null)
            }
        }
    }

    override fun validate(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptorWithConflicts = descriptor.validate()
}
