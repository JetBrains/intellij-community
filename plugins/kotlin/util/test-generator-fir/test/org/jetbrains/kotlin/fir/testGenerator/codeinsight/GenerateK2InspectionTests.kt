// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared.AbstractSharedK2LocalInspectionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2InspectionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2LocalInspectionTest
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
            model("code-insight/inspections-k2/tests/testData/inspectionsLocal", pattern = pattern)
        }

        testClass<AbstractK2InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("${idea}/inspections/redundantUnitReturnType", pattern = pattern)
        }
    }

    testGroup("code-insight/inspections-shared/tests/k2", testDataPath = "../testData") {
        testClass<AbstractSharedK2LocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("inspectionsLocal", pattern = pattern)
        }
    }
}