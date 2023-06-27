// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.structureView.AbstractKotlinGoToSuperDeclarationsHandlerTest
import org.jetbrains.kotlin.idea.k2.surroundWith.AbstractKotlinFirSurroundWithTest
import org.jetbrains.kotlin.testGenerator.model.*

internal fun MutableTWorkspace.generateK2CodeInsightTests() {
    generateK2InspectionTests()
    generateK2IntentionTests()
    generateK2StructureViewTests()
    generateK2PostfixTemplateTests()
    generateK2LineMarkerTests()

    testGroup("code-insight/kotlin.code-insight.k2") {
        testClass<AbstractKotlinGoToSuperDeclarationsHandlerTest> {
            model("gotoSuperDeclarationsHandler", pattern = Patterns.KT_WITHOUT_DOTS, passTestDataPath = false)
        }
        testClass<AbstractKotlinFirSurroundWithTest> {
            model("../../../idea/tests/testData/codeInsight/surroundWith/if", testMethodName = "doTestWithIfSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/ifElse", testMethodName = "doTestWithIfElseSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/ifElseExpression", testMethodName = "doTestWithIfElseExpressionSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/ifElseExpressionBraces", testMethodName = "doTestWithIfElseExpressionBracesSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/not", testMethodName = "doTestWithNotSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/parentheses", testMethodName = "doTestWithParenthesesSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/stringTemplate", testMethodName = "doTestWithStringTemplateSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/when", testMethodName = "doTestWithWhenSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatch", testMethodName = "doTestWithTryCatchSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatchExpression", testMethodName = "doTestWithTryCatchExpressionSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatchFinally", testMethodName = "doTestWithTryCatchFinallySurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatchFinallyExpression", testMethodName = "doTestWithTryCatchFinallyExpressionSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryFinally", testMethodName = "doTestWithTryFinallySurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/functionLiteral", testMethodName = "doTestWithFunctionLiteralSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/withIfExpression", testMethodName = "doTestWithSurroundWithIfExpression")
            model("../../../idea/tests/testData/codeInsight/surroundWith/withIfElseExpression", testMethodName = "doTestWithSurroundWithIfElseExpression")
        }
    }
}