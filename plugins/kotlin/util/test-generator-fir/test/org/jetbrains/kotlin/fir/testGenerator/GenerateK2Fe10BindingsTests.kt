// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.fe10bindings.inspections.AbstractFe10BindingIntentionTest
import org.jetbrains.kotlin.idea.k2.fe10bindings.inspections.AbstractFe10BindingLocalInspectionTest
import org.jetbrains.kotlin.idea.k2.fe10bindings.inspections.AbstractFe10BindingQuickFixTest
import org.jetbrains.kotlin.testGenerator.model.*

internal fun MutableTWorkspace.generateK2Fe10BindingsTests() {
    testGroup("k2-fe10-bindings", testDataPath = "../idea/tests") {
        testClass<AbstractFe10BindingIntentionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("testData/intentions/conventionNameCalls", pattern = pattern)
            model("testData/intentions/convertSecondaryConstructorToPrimary", pattern = pattern)
            model("testData/intentions/convertToStringTemplate", pattern = pattern)
            model("testData/intentions/convertTryFinallyToUseCall", pattern = pattern)
            model("testData/intentions/removeRedundantCallsOfConversionMethods", pattern = pattern)
        }
    }

    testGroup("k2-fe10-bindings", testDataPath = "../idea/tests") {
        testClass<AbstractFe10BindingLocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("testData/inspectionsLocal/addOperatorModifier", pattern = pattern)
            model("testData/inspectionsLocal/booleanLiteralArgument", pattern = pattern)
            model("testData/inspectionsLocal/convertSealedSubClassToObject", pattern = pattern)
            model("testData/inspectionsLocal/cascadeIf", pattern = pattern)
            model("testData/inspectionsLocal/collections/convertCallChainIntoSequence", pattern = pattern)
            model("testData/inspectionsLocal/convertNaNEquality", pattern = pattern)
            model("testData/inspectionsLocal/convertPairConstructorToToFunction", pattern = pattern)
            model("testData/inspectionsLocal/copyWithoutNamedArguments", pattern = pattern)
            model("testData/inspectionsLocal/branched/introduceWhenSubject", pattern = pattern)
            model("testData/inspectionsLocal/kdocMissingDocumentation", pattern = pattern)
            model("testData/inspectionsLocal/forEachParameterNotUsed", pattern = pattern)
            model("testData/inspectionsLocal/covariantEquals", pattern = pattern)
            model("testData/inspectionsLocal/lateinitVarOverridesLateinitVar", pattern = pattern)
            model("testData/inspectionsLocal/foldInitializerAndIfToElvis", pattern = pattern)
            model("testData/inspectionsLocal/mapGetWithNotNullAssertionOperator", pattern = pattern)
            model("testData/inspectionsLocal/memberVisibilityCanBePrivate", pattern = pattern)
            model("testData/inspectionsLocal/redundantObjectTypeCheck", pattern = pattern)
            model("testData/inspectionsLocal/redundantSuspend", pattern = pattern)
            model("testData/inspectionsLocal/redundantExplicitType", pattern = pattern)
            model("testData/inspectionsLocal/replaceArrayEqualityOpWithArraysEquals", pattern = pattern)
            model("testData/inspectionsLocal/replaceAssociateFunction", pattern = pattern)
            model("testData/inspectionsLocal/replaceIsEmptyWithIfEmpty", pattern = pattern)
        }
    }

    testGroup("k2-fe10-bindings", testDataPath = "../idea/tests") {
        testClass<AbstractFe10BindingQuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("testData/quickfix/addVarianceModifier", pattern = pattern)
            model("testData/quickfix/kdocMissingDocumentation", pattern = pattern)
            model("testData/quickfix/memberVisibilityCanBePrivate", pattern = pattern)
        }
    }
}