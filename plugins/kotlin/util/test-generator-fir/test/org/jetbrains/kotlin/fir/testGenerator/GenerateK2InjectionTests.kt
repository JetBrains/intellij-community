// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.injection.AbstractK2KotlinInMarkdownAnalysisTest
import org.jetbrains.kotlin.idea.k2.injection.AbstractK2KotlinInMarkdownHighlightingTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*

internal fun MutableTWorkspace.generateK2InjectionTests() {
    testGroup("injection/k2/tests", category = HIGHLIGHTING) {
        testClass<AbstractK2KotlinInMarkdownHighlightingTest> {
            model("kotlinInjectedInMarkdownHighlighting", pattern = Patterns.MD)
        }

        testClass<AbstractK2KotlinInMarkdownAnalysisTest> {
            model("kotlinInjectedInMarkdownHighlighting", pattern = Patterns.MD)
        }
    }
}