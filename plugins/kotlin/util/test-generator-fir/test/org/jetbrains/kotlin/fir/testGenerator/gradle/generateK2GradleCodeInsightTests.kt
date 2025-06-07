// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.gradle

import org.jetbrains.kotlin.idea.groovy.k2.AbstractGroovyLibraryDependenciesToBuildGradleKtsCopyPastePreprocessorTest
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.GRADLE
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2GradleCodeInsightTests() {
    testGroup("gradle/code-insight-groovy/tests.k2", category = GRADLE) {
        testClass<AbstractGroovyLibraryDependenciesToBuildGradleKtsCopyPastePreprocessorTest>() {
            model("groovyDependencyToKotlinDependency", pattern = Patterns.GROOVY)
        }
    }
}