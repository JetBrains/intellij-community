// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractK2SharedQuickFixTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractSharedK2InspectionTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractSharedK2LocalInspectionTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractSharedK2MultiFileQuickFixTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.idea.kdoc.AbstractSharedK2KDocHighlightingTest
import org.jetbrains.kotlin.idea.k2.inspections.tests.AbstractK2InspectionTest
import org.jetbrains.kotlin.idea.k2.inspections.tests.AbstractK2LocalInspectionAndGeneralHighlightingTest
import org.jetbrains.kotlin.idea.k2.inspections.tests.AbstractK2LocalInspectionTest
import org.jetbrains.kotlin.idea.k2.inspections.tests.AbstractK2MultiFileLocalInspectionTest
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2MultiFileQuickFixTest
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2QuickFixTest
import org.jetbrains.kotlin.testGenerator.model.*


internal fun MutableTWorkspace.generateK2InspectionTests() {
    val idea = "idea/tests/testData/"

    testGroup("code-insight/inspections-k2/tests", testDataPath = "../../..") {
        testClass<AbstractK2LocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("${idea}/inspectionsLocal/unusedVariable", pattern = pattern)
            model("${idea}/inspectionsLocal/redundantVisibilityModifier", pattern = pattern)
            model("${idea}/inspectionsLocal/implicitThis")
            model("${idea}/inspectionsLocal/doubleNegation")
            model("${idea}/inspectionsLocal/enumValuesSoftDeprecate")
            model("${idea}/inspectionsLocal/conventionNameCalls/replaceGetOrSet")
            model("${idea}/inspectionsLocal/nullableBooleanElvis")
            model("${idea}/inspectionsLocal/redundantElvisReturnNull")
            model("${idea}/inspectionsLocal/replaceCollectionCountWithSize")
            model("${idea}/inspectionsLocal/removeToStringInStringTemplate")
            model("${idea}/inspectionsLocal/liftOut/ifToAssignment")
            model("${idea}/inspectionsLocal/liftOut/tryToAssignment")
            model("${idea}/inspectionsLocal/liftOut/whenToAssignment")
            model("${idea}/inspectionsLocal/liftOut/ifToReturn")
            model("${idea}/inspectionsLocal/liftOut/tryToReturn")
            model("${idea}/inspectionsLocal/liftOut/whenToReturn")
            model("${idea}/inspectionsLocal/inconsistentCommentForJavaParameter")
            model("${idea}/inspectionsLocal/whenWithOnlyElse")
            model("${idea}/inspectionsLocal/equalsOrHashCode")
            model("${idea}/inspectionsLocal/removeRedundantQualifierName")
            model("${idea}/inspectionsLocal/redundantUnitExpression")
            model("${idea}/inspectionsLocal/equalsBetweenInconvertibleTypes")
            model("${idea}/inspectionsLocal/redundantIf")
            model("${idea}/inspectionsLocal/mayBeConstant")
            model("${idea}/inspectionsLocal/moveLambdaOutsideParentheses")
            model("code-insight/inspections-k2/tests/testData/inspectionsLocal", pattern = pattern)
        }
        /**
         * `unusedSymbol` tests require [com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass] to run,
         * so they extend the other base class [AbstractK2LocalInspectionAndGeneralHighlightingTest]
         */
        val packageName = AbstractK2LocalInspectionAndGeneralHighlightingTest::class.java.`package`.name
        val generatedClassName = "$packageName.K2UnusedSymbolHighlightingTestGenerated"
        testClass<AbstractK2LocalInspectionAndGeneralHighlightingTest>(generatedClassName) {
            model("${idea}/inspectionsLocal/unusedSymbol", pattern = Patterns.KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("${idea}/inspections/enumValuesSoftDeprecateInJava", pattern = pattern)
            model("${idea}/inspections/enumValuesSoftDeprecateInKotlin", pattern = pattern)
            model("${idea}/inspections/redundantUnitReturnType", pattern = pattern)
            model("${idea}/inspections/redundantIf", pattern = pattern)
            model("${idea}/inspections/equalsAndHashCode", pattern = pattern)
            model("${idea}/inspections/protectedInFinal", pattern = pattern)
            model("${idea}/intentions/convertToStringTemplate", pattern = pattern)
            model("${idea}/inspections/unusedSymbol", pattern = pattern)
        }

        testClass<AbstractK2QuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("${idea}/quickfix/redundantIf", pattern = pattern)
            model("${idea}/quickfix/changeSignature", pattern = pattern)
            model("${idea}/quickfix/redundantModalityModifier", pattern = pattern)
            model("${idea}/quickfix/removeToStringInStringTemplate", pattern = pattern)
            model("${idea}/quickfix/suppress", pattern = pattern)
            model("${idea}/quickfix/removeAnnotation", pattern = pattern)
            model("${idea}/quickfix/optIn", pattern = pattern)
            model("${idea}/quickfix/removeUseSiteTarget", pattern = pattern)
            model("${idea}/quickfix/protectedInFinal", pattern = pattern)
            model("${idea}/quickfix/createFromUsage/createFunction/call/abstract", pattern = pattern)
        }

        testClass<AbstractK2MultiFileQuickFixTest> {
            val pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$""")
            model("${idea}/quickfix/optIn", pattern = pattern, testMethodName = "doTestWithExtraFile")
        }

        testClass<AbstractK2MultiFileLocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.test$")
            model("${idea}/multiFileLocalInspections/unusedSymbol", pattern = pattern)
            model("${idea}/multiFileLocalInspections/redundantQualifierName", pattern = pattern)
            model("code-insight/inspections-k2/tests/testData/multiFileInspectionsLocal", pattern = pattern)
        }
    }

    testGroup("code-insight/inspections-shared/tests/k2", testDataPath = "../testData") {
        val relativeIdea = "../../../../$idea"

        testClass<AbstractSharedK2LocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("inspectionsLocal", pattern = pattern)
        }

        testClass<AbstractSharedK2InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("inspections", pattern = pattern)
            model("inspectionsLocal", pattern = pattern)
        }

        testClass<AbstractSharedK2KDocHighlightingTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("kdoc/highlighting", pattern = pattern)
        }

        testClass<AbstractK2SharedQuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("quickfix", pattern = pattern)

            model("${relativeIdea}/quickfix/optimizeImports", pattern = pattern)
        }

        testClass<AbstractSharedK2MultiFileQuickFixTest> {
            val pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$""")
            model("${relativeIdea}/quickfix/optimizeImports", pattern = pattern, testMethodName = "doTestWithExtraFile")
        }
    }
}
