// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.highlighting.*
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*

internal fun MutableTWorkspace.generateK2HighlighterTests() {
    testGroup("highlighting/highlighting-k2", category = HIGHLIGHTING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2HighlightingMetaInfoTest> {
            model("highlighterMetaInfo", pattern = Patterns.KT_OR_KTS)
        }

        testClass<AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest> {
            model("highlighterMetaInfoWithBundledCompilerPlugins")
        }

        testClass<AbstractK2HighlightingMetaInfoWithExtensionTest> {
            model("highlighterMetaInfoWithExtension")
        }

        testClass<AbstractK2HighlightExitPointsTest> {
            model("exitPoints")
        }

        testClass<AbstractK2HighlightUsagesTest> {
            model("highlightUsages")
        }
    }

    testGroup("highlighting/highlighting-k2", category = HIGHLIGHTING, testDataPath = "testData") {
        testClass<AbstractOutsiderHighlightingTest> {
            model("outsider", pattern = Patterns.DIRECTORY, isRecursive = false, passTestDataPath = false)
        }
    }
}