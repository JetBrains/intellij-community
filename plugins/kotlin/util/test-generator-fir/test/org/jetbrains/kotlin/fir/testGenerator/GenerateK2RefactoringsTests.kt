// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.refactoring.bindToElement.AbstractK2BindToElementTest
import org.jetbrains.kotlin.idea.k2.refactoring.bindToElement.AbstractK2BindToFqnTest
import org.jetbrains.kotlin.idea.k2.refactoring.copy.AbstractK2CopyTest
import org.jetbrains.kotlin.idea.k2.refactoring.copy.AbstractK2MultiModuleCopyTest
import org.jetbrains.kotlin.idea.k2.refactoring.inline.AbstractInlineTestWithSomeDescriptors
import org.jetbrains.kotlin.idea.k2.refactoring.inline.AbstractKotlinFirInlineTest
import org.jetbrains.kotlin.idea.k2.refactoring.inline.AbstractKotlinFirMultiplatformTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2ExtractionTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2InplaceIntroduceFunctionTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2IntroduceConstantTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2IntroduceFunctionTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2IntroduceFunctionWithExtractFunctionModifierTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2IntroduceParameterTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2IntroducePropertyTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2IntroduceTypeAliasTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.AbstractK2PsiUnifierTest
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.AbstractK2IntroduceVariableTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2ChangePackageTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MoveDirectoryTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MoveFileOrDirectoriesTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MoveNestedTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MovePackageTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MoveTopLevelTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MoveTopLevelToInnerTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.AbstractK2MultiModuleMoveTest
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.AbstractK2MoveToClassWithConversionTest
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.AbstractK2PullUpTest
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.AbstractK2PushDownTest
import org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.AbstractFirMultiModuleSafeDeleteTest
import org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.AbstractK2SafeDeleteTest
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.EXTRACT_REFACTORING
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.INLINE_REFACTORING
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.MOVE_REFACTORING
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.REFACTORING
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.TEST
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2RefactoringsTests() {
    testGroup("refactorings/kotlin.refactorings.tests.k2", category = REFACTORING) {
        testClass<AbstractK2SafeDeleteTest> {
            model("safeDelete/deleteClass/kotlinClass", testMethodName = "doClassTest")
            //todo secondary constructor
            model("safeDelete/deleteClass/kotlinClassWithJava", testMethodName = "doClassTestWithJava")
            model("safeDelete/deleteClass/javaClassWithKotlin", pattern = Patterns.JAVA, testMethodName = "doJavaClassTest")
            model("safeDelete/deleteObject/kotlinObject", testMethodName = "doObjectTest")
            model("safeDelete/deleteFunction/kotlinFunction", testMethodName = "doFunctionTest")
            model(
                "safeDelete/deleteFunction/kotlinFunctionWithJava",
                Patterns.forRegex("^(((?!secondary)(?!implement4).)+)\\.kt"), //todo secondary constructor, super method search from java override
                testMethodName = "doFunctionTestWithJava"
            )
            model("safeDelete/deleteFunction/javaFunctionWithKotlin", testMethodName = "doJavaMethodTest")
            model("safeDelete/deleteProperty/kotlinProperty", testMethodName = "doPropertyTest")
            model("safeDelete/deleteProperty/kotlinPropertyWithJava", testMethodName = "doPropertyTestWithJava")//todo  super method search from java override
            model("safeDelete/deleteProperty/javaPropertyWithKotlin", testMethodName = "doJavaPropertyTest")
            model("safeDelete/deleteTypeAlias/kotlinTypeAlias", testMethodName = "doTypeAliasTest")
            model("safeDelete/deleteTypeParameter/kotlinTypeParameter", testMethodName = "doTypeParameterTest")
            model("safeDelete/deleteTypeParameter/kotlinTypeParameterWithJava", testMethodName = "doTypeParameterTestWithJava")
            model("safeDelete/deleteValueParameter/kotlinValueParameter", testMethodName = "doValueParameterTest")
            model(
                "safeDelete/deleteValueParameter/kotlinValueParameterWithJava",
                testMethodName = "doValueParameterTestWithJava"
            )
            model(
                "safeDelete/deleteValueParameter/javaParameterWithKotlin",
                pattern = Patterns.JAVA,
                testMethodName = "doJavaParameterTest"
            )
        }

        testClass<AbstractFirMultiModuleSafeDeleteTest> {
            model("safeDeleteMultiModule", pattern = TEST, flatten = true)
        }

        testClass<AbstractK2BindToElementTest> {
            model("bindToFqn")
            model("bindToElement")
        }
        testClass<AbstractK2BindToFqnTest> {
            model("bindToFqn")
        }
    }
    testGroup("refactorings/kotlin.refactorings.tests.k2", category = INLINE_REFACTORING) {
        testClass<AbstractKotlinFirInlineTest> {
            model("inline", pattern = Patterns.KT_WITHOUT_DOTS, excludedDirectories = listOf("withFullJdk"))
        }

        testClass<AbstractInlineTestWithSomeDescriptors> {
            model("inline/withFullJdk", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractKotlinFirMultiplatformTest> {
            model("inlineMultiModule", pattern = Patterns.KT_WITHOUT_DOTS)
        }
    }
    testGroup("refactorings/kotlin.refactorings.tests.k2", category = EXTRACT_REFACTORING) {
        testClass<AbstractK2IntroduceFunctionTest> {
            model("extractFunction", pattern = Patterns.KT_OR_KTS, testMethodName = "doExtractFunctionTest")
        }

        testClass<AbstractK2IntroduceTypeAliasTest> {
            model("introduceTypeAlias", pattern = Patterns.KT_OR_KTS, testMethodName = "doIntroduceTypeAliasTest")
        }

        testClass<AbstractK2IntroduceFunctionWithExtractFunctionModifierTest> {
            model("extractFunctionModifier", pattern = Patterns.KT_OR_KTS, testMethodName = "doExtractFunctionTest")
        }

        testClass<AbstractK2InplaceIntroduceFunctionTest> {
            model("extractFunctionInplace")
        }

        testClass<AbstractK2IntroduceParameterTest> {
            model("introduceParameter", pattern = Patterns.KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceParameterTest")
            model("introduceJavaParameter", pattern = Patterns.JAVA, testMethodName = "doIntroduceJavaParameterTest")
            model("introduceLambdaParameter", pattern = Patterns.KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceFunctionalParameterTest")
        }

        testClass<AbstractK2IntroducePropertyTest> {
            model("introduceProperty", pattern = Patterns.KT_OR_KTS, testMethodName = "doIntroducePropertyTest")
        }

        testClass<AbstractK2IntroduceConstantTest> {
            model("introduceConstant", pattern = Patterns.KT_OR_KTS, testMethodName = "doIntroduceConstantTest")
        }
    }

    testGroup("refactorings/kotlin.refactorings.tests.k2", category = REFACTORING) {
        testClass< AbstractK2CopyTest> {
            model("copy", pattern = TEST, flatten = true)
        }

        testClass<AbstractK2MultiModuleCopyTest> {
            model("copyMultiModule", pattern = TEST, flatten = true)
        }
    }

    testGroup("refactorings/kotlin.refactorings.tests.k2", category = MOVE_REFACTORING) {
        testClass<AbstractK2ChangePackageTest> {
            model("changePackage", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MovePackageTest> {
            model("movePackage", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveDirectoryTest> {
            model("moveDirectory", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveFileOrDirectoriesTest> {
            model("moveFile", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveTopLevelTest> {
            model("moveTopLevel", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveTopLevelToInnerTest> {
            model("moveTopLevelToInner", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveNestedTest> {
            model("moveNested", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MultiModuleMoveTest> {
            model("moveMultiModule", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2MoveToClassWithConversionTest> {
            model("moveToClassWithConversion", pattern = TEST, flatten = true)
        }
    }

    testGroup("refactorings/kotlin.refactorings.tests.k2", category = EXTRACT_REFACTORING) {
        testClass<AbstractK2IntroduceVariableTest> {
            model("introduceVariable", pattern = Patterns.KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceVariableTest")
        }
        testClass<AbstractK2PsiUnifierTest> {
            model("../../../idea/tests/testData/unifier")
        }
        testClass<AbstractK2PullUpTest> {
            model("pullUp/k2k", pattern = KT, flatten = true, testClassName = "K2K", testMethodName = "doKotlinTest")
            model("pullUp/k2j", pattern = KT, flatten = true, testClassName = "K2J", testMethodName = "doKotlinTest")
        }
        testClass<AbstractK2PushDownTest> {
            model("pushDown/k2k", pattern = KT, flatten = true, testClassName = "K2K", testMethodName = "doKotlinTest")
            model("pushDown/k2j", pattern = KT, flatten = true, testClassName = "K2J", testMethodName = "doKotlinTest")
        }
        testClass<AbstractK2ExtractionTest> {
            model("extractSuperclass", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doExtractSuperclassTest")
            model("extractInterface", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doExtractInterfaceTest")
        }
    }
}