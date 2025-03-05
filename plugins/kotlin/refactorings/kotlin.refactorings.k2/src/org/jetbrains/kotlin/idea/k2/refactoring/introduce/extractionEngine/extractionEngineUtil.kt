// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.*
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionEngine

fun createExtractionEngine(helper: ExtractionEngineHelper): IExtractionEngine<KaType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts> {
    val engine = object :
        IExtractionEngine<KaType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts>(
            helper
        ) {
        override fun performAnalysis(extractionData: ExtractionData): AnalysisResult<KaType> {
            return ExtractionDataAnalyzer(extractionData).performAnalysis()
        }
    }
    return engine
}