// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.refactoring.bindToElement.AbstractK2BindToElementTest
import org.jetbrains.kotlin.idea.k2.refactoring.bindToElement.AbstractK2BindToFqnTest
import org.jetbrains.kotlin.idea.k2.refactoring.copy.AbstractK2CopyTest
import org.jetbrains.kotlin.idea.k2.refactoring.copy.AbstractK2MultiModuleCopyTest
import org.jetbrains.kotlin.idea.k2.refactoring.inline.AbstractKotlinFirInlineTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.*
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.AbstractK2IntroduceVariableTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.*
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.AbstractK2PullUpTest
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.AbstractK2PushDownTest
import org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.AbstractFirMultiModuleSafeDeleteTest
import org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.AbstractK2SafeDeleteTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.TEST

internal fun MutableTWorkspace.generateK2RefactoringsTests() {
    testGroup("refactorings/kotlin.refactorings.tests.k2", category = REFACTORING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2SafeDeleteTest> {
            model("refactoring/safeDelete/deleteClass/kotlinClass", testMethodName = "doClassTest")
            //todo secondary constructor
            //model("refactoring/safeDelete/deleteClass/kotlinClassWithJava", testMethodName = "doClassTestWithJava")
            model("refactoring/safeDelete/deleteClass/javaClassWithKotlin", pattern = Patterns.JAVA, testMethodName = "doJavaClassTest")
            model("refactoring/safeDelete/deleteObject/kotlinObject", testMethodName = "doObjectTest")
            model("refactoring/safeDelete/deleteFunction/kotlinFunction", testMethodName = "doFunctionTest")
            model(
                "refactoring/safeDelete/deleteFunction/kotlinFunctionWithJava",
                Patterns.forRegex("^(((?!secondary)(?!implement4).)+)\\.kt"), //todo secondary constructor, super method search from java override
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
            model(
                "refactoring/safeDelete/deleteValueParameter/kotlinValueParameterWithJava",
                testMethodName = "doValueParameterTestWithJava"
            )
            model(
                "refactoring/safeDelete/deleteValueParameter/javaParameterWithKotlin",
                pattern = Patterns.JAVA,
                testMethodName = "doJavaParameterTest"
            )
        }

        testClass<AbstractFirMultiModuleSafeDeleteTest> {
            model("refactoring/safeDeleteMultiModule", pattern = TEST, flatten = true)
        }

        testClass<AbstractK2BindToElementTest> {
            model("refactoring/bindToFqn")
            model("refactoring/bindToElement")
        }
        testClass<AbstractK2BindToFqnTest> {
            model("refactoring/bindToFqn")
        }
    }
    testGroup("refactorings/kotlin.refactorings.tests.k2", category = INLINE_REFACTORING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractKotlinFirInlineTest> {
            model("refactoring/inline", pattern = Patterns.KT_WITHOUT_DOTS, excludedDirectories = listOf("withFullJdk"))
        }
    }
    testGroup("refactorings/kotlin.refactorings.tests.k2", category = EXTRACT_REFACTORING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2IntroduceFunctionTest> {
            model("refactoring/extractFunction", pattern = Patterns.KT_OR_KTS, testMethodName = "doExtractFunctionTest")
        }

        testClass<AbstractK2IntroduceTypeAliasTest> {
            model("refactoring/introduceTypeAlias", pattern = Patterns.KT_OR_KTS, testMethodName = "doIntroduceTypeAliasTest")
        }

        testClass<AbstractK2IntroduceFunctionWithExtractFunctionModifierTest> {
            model("refactoring/extractFunctionModifier", pattern = Patterns.KT_OR_KTS, testMethodName = "doExtractFunctionTest")
        }

        testClass<AbstractK2InplaceIntroduceFunctionTest> {
            model("refactoring/extractFunctionInplace")
        }

        testClass<AbstractK2IntroduceParameterTest> {
            model("refactoring/introduceParameter", pattern = Patterns.KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceParameterTest")
            model("refactoring/introduceJavaParameter", pattern = Patterns.JAVA, testMethodName = "doIntroduceJavaParameterTest")
            model("refactoring/introduceLambdaParameter", pattern = Patterns.KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceFunctionalParameterTest")
        }

        testClass<AbstractK2IntroducePropertyTest> {
            model("refactoring/introduceProperty", pattern = Patterns.KT_OR_KTS, testMethodName = "doIntroducePropertyTest")
        }

        testClass<AbstractK2IntroduceConstantTest> {
            model("refactoring/introduceConstant", pattern = Patterns.KT_OR_KTS, testMethodName = "doIntroduceConstantTest")
        }
    }

    testGroup("refactorings/kotlin.refactorings.tests.k2", category = REFACTORING, testDataPath = "../../idea/tests/testData") {
        testClass< AbstractK2CopyTest> {
            model("refactoring/copy", pattern = TEST, flatten = true)
        }

        testClass<AbstractK2MultiModuleCopyTest> {
            model("refactoring/copyMultiModule", pattern = TEST, flatten = true)
        }
    }

    testGroup("refactorings/kotlin.refactorings.move.k2", category = MOVE_REFACTORING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2ChangePackageTest> {
            model("refactoring/changePackage", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MovePackageTest> {
            model("refactoring/movePackage", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveDirectoryTest> {
            model("refactoring/moveDirectory", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveFileOrDirectoriesTest> {
            model("refactoring/moveFile", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveTopLevelTest> {
            model("refactoring/moveTopLevel", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveTopLevelToInnerTest> {
            model("refactoring/moveTopLevelToInner", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveNestedTest> {
            model("refactoring/moveNested", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MultiModuleMoveTest> {
            model("refactoring/moveMultiModule", pattern = TEST, flatten = true)
        }
    }

    testGroup("refactorings/kotlin.refactorings.tests.k2", category = EXTRACT_REFACTORING, testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2IntroduceVariableTest> {
            model("refactoring/introduceVariable", pattern = Patterns.KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceVariableTest")
        }
        testClass<AbstractK2PsiUnifierTest> {
            model("unifier")
        }
        testClass<AbstractK2PullUpTest> {
            model("refactoring/pullUp/k2k", pattern = KT, flatten = true, testClassName = "K2K", testMethodName = "doKotlinTest")
            model("refactoring/pullUp/k2j", pattern = KT, flatten = true, testClassName = "K2J", testMethodName = "doKotlinTest")
        }
        testClass<AbstractK2PushDownTest> {
            model("refactoring/pushDown/k2k", pattern = KT, flatten = true, testClassName = "K2K", testMethodName = "doKotlinTest")
            model("refactoring/pushDown/k2j", pattern = KT, flatten = true, testClassName = "K2J", testMethodName = "doKotlinTest")
        }
        testClass<AbstractK2ExtractionTest> {
            model("refactoring/extractSuperclass", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doExtractSuperclassTest")
            model("refactoring/extractInterface", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doExtractInterfaceTest")
        }
    }
}