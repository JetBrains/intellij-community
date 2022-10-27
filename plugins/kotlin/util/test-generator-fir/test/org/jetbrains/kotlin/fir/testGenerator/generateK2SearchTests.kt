// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.inheritorsSearch.AbstractDirectKotlinInheritorsSearcherTest
import org.jetbrains.kotlin.idea.k2.inheritorsSearch.AbstractKotlinDefinitionsSearchTest
import org.jetbrains.kotlin.idea.k2.search.AbstractFirAnnotatedMembersSearchTest
import org.jetbrains.kotlin.testGenerator.model.*

internal fun MutableTWorkspace.generateK2SearchTests() {
    testGroup("kotlin.searching/kotlin.searching.test.k2", testDataPath = "../testData") {
        testClass<AbstractDirectKotlinInheritorsSearcherTest> {
            model("inheritorsSearch/kotlinClass", testMethodName = "doTestKotlinClass", pattern = Patterns.KT_WITHOUT_DOTS)
            model("inheritorsSearch/javaClass", testMethodName = "doTestJavaClass", pattern = Patterns.JAVA)
            model("inheritorsSearch/kotlinFunction", testMethodName = "doTestKotlinFunction", pattern = Patterns.KT_WITHOUT_DOTS)
        }
        testClass<AbstractKotlinDefinitionsSearchTest> {
            model("definitionsSearch/kotlinClass", testMethodName = "doTestKotlinClass", pattern = Patterns.KT_WITHOUT_DOTS)
            model("definitionsSearch/javaClass", testMethodName = "doTestJavaClass", pattern = Patterns.JAVA)
            model("definitionsSearch/kotlinFunction", testMethodName = "doTestKotlinFunction", pattern = Patterns.KT_WITHOUT_DOTS)
        }
        testClass<AbstractFirAnnotatedMembersSearchTest> {
            model("../../idea/tests/testData/search/annotations")
        }
    }
}