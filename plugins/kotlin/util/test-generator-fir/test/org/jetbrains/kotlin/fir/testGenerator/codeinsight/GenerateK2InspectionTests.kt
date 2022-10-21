// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared.AbstractK2SharedQuickFixTest
import org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared.AbstractSharedK2InspectionTest
import org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared.AbstractSharedK2LocalInspectionTest
import org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared.idea.kdoc.AbstractSharedK2KDocHighlightingTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2InspectionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2LocalInspectionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2QuickFixTest
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
            model("${idea}/inspectionsLocal/conventionNameCalls/replaceGetOrSet")
            model("${idea}/inspectionsLocal/nullableBooleanElvis")
            model("${idea}/inspectionsLocal/redundantElvisReturnNull")
            model("${idea}/inspectionsLocal/replaceCollectionCountWithSize")
            model("${idea}/inspectionsLocal/removeToStringInStringTemplate")
            model("${idea}/inspectionsLocal/liftOut/ifToAssignment")
            model("${idea}/inspectionsLocal/liftOut/tryToAssignment")
            model("${idea}/inspectionsLocal/liftOut/whenToAssignment")
            model("code-insight/inspections-k2/tests/testData/inspectionsLocal", pattern = pattern)
        }

        testClass<AbstractK2InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("${idea}/inspections/redundantUnitReturnType", pattern = pattern)
            model("${idea}/inspections/redundantIf", pattern = pattern)
        }

        testClass<AbstractK2QuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("${idea}/quickfix/redundantIf", pattern = pattern)
            model("${idea}/quickfix/redundantModalityModifier", pattern = pattern)
            model("${idea}/quickfix/removeToStringInStringTemplate", pattern = pattern)
        }
    }

    testGroup("code-insight/inspections-shared/tests/k2", testDataPath = "../testData") {
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
            model("quickfix", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }
}
