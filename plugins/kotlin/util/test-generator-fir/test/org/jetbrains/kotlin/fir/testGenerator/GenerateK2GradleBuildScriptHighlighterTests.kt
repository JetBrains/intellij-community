// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.codeInsight.gradle.highlighting.AbstractK2GradleBuildFileHighlightingTest
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.GRADLE
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2GradleBuildScriptHighlighterTests() {
    testGroup("gradle/gradle-java/k2", testDataPath = "../../../idea/tests/testData", category = GRADLE) {
        testClass<AbstractK2GradleBuildFileHighlightingTest> {
            model(
                "gradle/highlighting/gradle8",
                pattern = Patterns.DIRECTORY,
                isRecursive = false,
                setUpStatements = listOf("setGradleVersion(\"8.6\");")
            )
            model(
                "gradle/highlighting/gradle7",
                pattern = Patterns.DIRECTORY,
                isRecursive = false,
                setUpStatements = listOf("setGradleVersion(\"7.6.4\");")
            )
        }
    }
}