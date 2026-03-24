// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.core.script.k2.definitions.AbstractScriptWithBundledCompilerPluginHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2ComposeCompilerPluginCheckerTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightExitPointsTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightUsagesTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractK2HighlightingMetaInfoWithExtensionTest
import org.jetbrains.kotlin.idea.core.script.k2.definitions.AbstractScriptHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.k2.highlighting.AbstractOutsiderHighlightingTest
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.HIGHLIGHTING
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup
import org.jetbrains.kotlin.testGenerator.model.toJavaIdentifier
import java.io.File

internal fun MutableTWorkspace.generateK2HighlighterTests() {
    testGroup("highlighting/highlighting-k2", category = HIGHLIGHTING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2HighlightingMetaInfoTest> {
            model("highlighterMetaInfo", pattern = Patterns.KT)
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

        testClass<AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest> {
            model("highlighterMetaInfoWithBundledCompilerPlugins", pattern = Patterns.KT)
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

    testGroup("base/scripting/scripting.k2", category = HIGHLIGHTING, testDataPath = "../../../idea/tests/testData") {
        testClass<AbstractScriptHighlightingMetaInfoTest>(
            generatedPackagePostfix = "generated",
        ) {
            model("highlighterMetaInfo", pattern = Patterns.KTS)
            model("mainKts/highlighting", testClassName = File("mainKts/highlighting/singleFile").toJavaIdentifier().capitalize(), pattern = Patterns.MAIN_KTS)
            model("mainKts/highlighting", testClassName = File("mainKts/highlighting/multiFiles").toJavaIdentifier().capitalize(),pattern = Patterns.TEST)
        }

        testClass<AbstractScriptWithBundledCompilerPluginHighlightingMetaInfoTest>(
            generatedPackagePostfix = "generated",
        ) {
            model("highlighterMetaInfoWithBundledCompilerPlugins", pattern = Patterns.KTS)
        }
    }

    testGroup("highlighting/highlighting-k2", category = HIGHLIGHTING, testDataPath = "testData") {
        testClass<AbstractOutsiderHighlightingTest> {
            model("outsider", pattern = Patterns.DIRECTORY, isRecursive = false, passTestDataPath = false)
        }
    }
}