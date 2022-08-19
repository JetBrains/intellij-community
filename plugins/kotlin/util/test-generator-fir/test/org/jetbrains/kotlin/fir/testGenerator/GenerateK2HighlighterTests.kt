// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightingMetaInfoTest
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2HighlighterTests() {
    testGroup("highlighting", testDataPath = "../idea/tests/testData") {
        testClass<AbstractK2HighlightingMetaInfoTest> {
            model("highlighterMetaInfo")
        }
    }
}