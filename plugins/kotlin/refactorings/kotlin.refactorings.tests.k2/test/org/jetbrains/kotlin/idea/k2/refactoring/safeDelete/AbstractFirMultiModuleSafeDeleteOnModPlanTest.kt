// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.openapi.util.registry.Registry
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow
import org.jetbrains.kotlin.idea.refactoring.safeDelete.KotlinSafeDeleteSettings

/**
 * The data of [AbstractFirMultiModuleSafeDeleteTest], run with the interactive flow on the ModPlan engine instead of the legacy one. A
 * test that fails here is a difference of the ModPlan flow from the legacy one.
 *
 * The answer to the question about the expected declaration is the setting that the test sets, as the legacy unit-test mode reads it.
 */
abstract class AbstractFirMultiModuleSafeDeleteOnModPlanTest : AbstractFirMultiModuleSafeDeleteTest() {
    override fun setUp() {
        super.setUp()
        Registry.get("refactoring.safeDelete.modPlan").setValue(true, testRootDisposable)
        ModPlanSafeDeleteFlow.answerInTests({ options ->
            val liftToExpected = with(KotlinSafeDeleteSettings) { project.ALLOW_LIFTING_ACTUAL_PARAMETER_TO_EXPECTED }
            options.with(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL, liftToExpected)
        }, testRootDisposable)
        ModPlanSafeDeleteFlow.failOnLegacyInTests(testRootDisposable)
    }
}
