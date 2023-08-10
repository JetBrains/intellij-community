// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MoveTest
import org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.AbstractK2BindToElementTest
import org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.AbstractK2SafeDeleteTest
import org.jetbrains.kotlin.testGenerator.model.*

internal fun MutableTWorkspace.generateK2RefactoringsTests() {
    testGroup("refactorings/kotlin.refactorings.tests.k2", testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2SafeDeleteTest> {
            model("refactoring/safeDelete/deleteClass/kotlinClass", testMethodName = "doClassTest")
            //todo secondary constructor 
            //model("refactoring/safeDelete/deleteClass/kotlinClassWithJava", testMethodName = "doClassTestWithJava")
            model("refactoring/safeDelete/deleteClass/javaClassWithKotlin", pattern = Patterns.JAVA, testMethodName = "doJavaClassTest")
            model("refactoring/safeDelete/deleteObject/kotlinObject", testMethodName = "doObjectTest")
            model("refactoring/safeDelete/deleteFunction/kotlinFunction", testMethodName = "doFunctionTest")
            model(
                "refactoring/safeDelete/deleteFunction/kotlinFunctionWithJava",
                Patterns.forRegex("^(((?!secondary)(?!implement4).)+)\\.kt"),//todo secondary constructor, super method search from java override
                testMethodName = "doFunctionTestWithJava"
            )
            model("refactoring/safeDelete/deleteFunction/javaFunctionWithKotlin", testMethodName = "doJavaMethodTest")
            model("refactoring/safeDelete/deleteProperty/kotlinProperty", testMethodName = "doPropertyTest")
            //model("refactoring/safeDelete/deleteProperty/kotlinPropertyWithJava", testMethodName = "doPropertyTestWithJava")//todo  super method search from java override
            model("refactoring/safeDelete/deleteProperty/javaPropertyWithKotlin", testMethodName = "doJavaPropertyTest")
            model("refactoring/safeDelete/deleteTypeAlias/kotlinTypeAlias", testMethodName = "doTypeAliasTest")
            model("refactoring/safeDelete/deleteTypeParameter/kotlinTypeParameter", testMethodName = "doTypeParameterTest")
            model("refactoring/safeDelete/deleteTypeParameter/kotlinTypeParameterWithJava", testMethodName = "doTypeParameterTestWithJava")
            model("refactoring/safeDelete/deleteValueParameter/kotlinValueParameter", testMethodName = "doValueParameterTest")
            model("refactoring/safeDelete/deleteValueParameter/kotlinValueParameterWithJava",  testMethodName = "doValueParameterTestWithJava")
            model("refactoring/safeDelete/deleteValueParameter/javaParameterWithKotlin", pattern = Patterns.JAVA, testMethodName = "doJavaParameterTest")
        }
        testClass<AbstractK2BindToElementTest> {
            model("refactoring/bindToElement/callableReference")
            model("refactoring/bindToElement/constructorCall")
            model("refactoring/bindToElement/superTypeCall")
            model("refactoring/bindToElement/annotationCall")
            model("refactoring/bindToElement/propertyTypeReference")
            model("refactoring/bindToElement/returnTypeReference")
            model("refactoring/bindToElement/superTypeReference")
            model("refactoring/bindToElement/delegatedSuperTypeReference")
            model("refactoring/bindToElement/annotationReference")
            model("refactoring/bindToElement/changeImport")
        }
    }

    testGroup("refactorings/kotlin.refactorings.move.k2", testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2MoveTest> {
            model("refactoring/move", pattern = Patterns.TEST, flatten = true)
        }
    }
}