// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2BundledCompilerPluginsInScriptHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2ComposeCompilerPluginCheckerTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightExitPointsTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightUsagesTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightingMetaInfoWithExtensionTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2ScriptHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2ScriptHighlightingTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractOutsiderHighlightingTest
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.HIGHLIGHTING
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2HighlighterTests() {
    testGroup("highlighting/highlighting-k2", category = HIGHLIGHTING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2HighlightingMetaInfoTest> {
            model("highlighterMetaInfo", pattern = Patterns.KT)
        }
        testClass<AbstractK2ScriptHighlightingMetaInfoTest> {
            model("highlighterMetaInfo", pattern = Patterns.KTS)
        }

        testClass<AbstractK2ScriptHighlightingTest> {
            model("mainKts/highlighting", pattern = Patterns.MAIN_KTS)
        }

        testClass<AbstractK2HighlightingMetaInfoTest>(
            platforms = KMPTestPlatform.ALL_SPECIFIED - KMPTestPlatform.Jvm,
            generatedPackagePostfix = "metaInfoKmp",
        ) {
            model(
                "highlighterMetaInfo", pattern = Patterns.KT,
                excludedDirectories = listOf("jvm")
            )
        }
        testClass<AbstractK2ScriptHighlightingMetaInfoTest>(
            platforms = KMPTestPlatform.ALL_SPECIFIED - KMPTestPlatform.Jvm,
            generatedPackagePostfix = "metaInfoKmp",
        ) {
            model(
                "highlighterMetaInfo", pattern = Patterns.KTS,
                excludedDirectories = listOf("jvm")
            )
        }

        testClass<AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest> {
            model("highlighterMetaInfoWithBundledCompilerPlugins", pattern = Patterns.KT)
        }
        testClass<AbstractK2BundledCompilerPluginsInScriptHighlightingMetaInfoTest> {
            model("highlighterMetaInfoWithBundledCompilerPlugins", pattern = Patterns.KTS)
        }

        testClass<AbstractK2ComposeCompilerPluginCheckerTest> {
            model("highlighterMetaInfoWithComposeCompilerCheckers")
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