// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.analysisApi.tests.components.javaInteroperability.AbstractKaSymbolByPsiClassTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*

internal fun MutableTWorkspace.generateK2AnalysisApiTests() {
    testGroup("base/analysis-api/analysis-api-k2-tests", category = ANALYSIS_API) {
        testClass<AbstractKaSymbolByPsiClassTest> {
            model("javaInteroperability", pattern = Patterns.KT_WITHOUT_DOTS)
        }
    }
}