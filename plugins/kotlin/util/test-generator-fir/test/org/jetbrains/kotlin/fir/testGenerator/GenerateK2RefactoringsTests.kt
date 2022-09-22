// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.refactoring.safeDelete.AbstractK2SafeDeleteTest
import org.jetbrains.kotlin.testGenerator.model.*

internal fun MutableTWorkspace.generateK2RefactoringsTests() {
    testGroup("refactorings/kotlin.refactorings.tests.k2", testDataPath = "../../idea/tests/testData") {
        val noHierarchy = Patterns.forRegex("^(((?![hH]ierarchy)(?![iI]mplement)(?![oO]verride).)+)\\.kt")
        testClass<AbstractK2SafeDeleteTest> {
            model("refactoring/safeDelete/deleteClass/kotlinClass", testMethodName = "doClassTest")
            //todo secondary constructor 
            //model("refactoring/safeDelete/deleteClass/kotlinClassWithJava", testMethodName = "doClassTestWithJava")
            //model("refactoring/safeDelete/deleteClass/javaClassWithKotlin", pattern = Patterns.JAVA, testMethodName = "doJavaClassTest")
            model("refactoring/safeDelete/deleteObject/kotlinObject", testMethodName = "doObjectTest")
            model("refactoring/safeDelete/deleteFunction/kotlinFunction", pattern = noHierarchy, testMethodName = "doFunctionTest")
            //model("refactoring/safeDelete/deleteFunction/kotlinFunctionWithJava", testMethodName = "doFunctionTestWithJava")
            //model("refactoring/safeDelete/deleteFunction/javaFunctionWithKotlin", testMethodName = "doJavaMethodTest")
            model("refactoring/safeDelete/deleteProperty/kotlinProperty", pattern = noHierarchy, testMethodName = "doPropertyTest")
            //model("refactoring/safeDelete/deleteProperty/kotlinPropertyWithJava", pattern = noHierarchy,  testMethodName = "doPropertyTestWithJava")
            model("refactoring/safeDelete/deleteProperty/javaPropertyWithKotlin", testMethodName = "doJavaPropertyTest")
            model("refactoring/safeDelete/deleteTypeAlias/kotlinTypeAlias", testMethodName = "doTypeAliasTest")
            model("refactoring/safeDelete/deleteTypeParameter/kotlinTypeParameter", testMethodName = "doTypeParameterTest")
            model("refactoring/safeDelete/deleteTypeParameter/kotlinTypeParameterWithJava", testMethodName = "doTypeParameterTestWithJava")
            model("refactoring/safeDelete/deleteValueParameter/kotlinValueParameter", pattern = noHierarchy, testMethodName = "doValueParameterTest")
            model("refactoring/safeDelete/deleteValueParameter/kotlinValueParameterWithJava", pattern = noHierarchy,  testMethodName = "doValueParameterTestWithJava")
            model("refactoring/safeDelete/deleteValueParameter/javaParameterWithKotlin", pattern = Patterns.JAVA, testMethodName = "doJavaParameterTest")
        }
    }
}