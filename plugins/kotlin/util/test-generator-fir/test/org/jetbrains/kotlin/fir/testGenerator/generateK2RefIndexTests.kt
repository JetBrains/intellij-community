// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.fir.search.refIndex.AbstractFindUsagesWithCompilerReferenceIndexFirTest
import org.jetbrains.kotlin.idea.fir.search.refIndex.AbstractKotlinCompilerReferenceByReferenceFirTest
import org.jetbrains.kotlin.idea.fir.search.refIndex.AbstractKotlinCompilerReferenceFirTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*

internal fun MutableTWorkspace.generateK2RefIndexTests() {
    testGroup("compiler-reference-index/tests.k2", testDataPath = "../../idea/tests/testData", category = FIND_USAGES) {
        testClass<AbstractFindUsagesWithCompilerReferenceIndexFirTest> {
            model("findUsages/kotlin", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""), classPerTest = true)
            model("findUsages/java", pattern = Patterns.forRegex("""^(.+)\.0\.java$"""), classPerTest = true)
            model("findUsages/propertyFiles", pattern = Patterns.forRegex("""^(.+)\.0\.properties$"""), classPerTest = true)
        }
    }

    testGroup("compiler-reference-index/tests.k2", testDataPath = "../tests/testData", category = FIND_USAGES) {
        testClass<AbstractKotlinCompilerReferenceFirTest> {
            model("compilerIndex", pattern = Patterns.DIRECTORY, classPerTest = true)
        }
        testClass<AbstractKotlinCompilerReferenceByReferenceFirTest> {
            model("compilerIndexByReference", pattern = Patterns.DIRECTORY, classPerTest = true)
        }
    }
}