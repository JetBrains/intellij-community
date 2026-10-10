// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.testFramework.SafeDeleteTestSupport.headlessSafeDelete

/**
 * Runs the data of [AbstractFirMultiModuleSafeDeleteTest] through [headlessSafeDelete], as the MCP tool runs it.
 */
abstract class AbstractK2HeadlessMultiModuleSafeDeleteTest : AbstractFirMultiModuleSafeDeleteTest() {

    override fun setUp() {
        super.setUp()
        SafeDeleteAction.headlessRunner = { element, config ->
            val liftParameterToExpected = config.get("liftParameterToExpected")?.asBoolean ?: true
            val optionValues = ModPlanOptionValues.EMPTY.with(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL, liftParameterToExpected)
            // The second run of runRefactoringTest ignores the conflicts, as `force` would: the write leaves the blocking usages.
            headlessSafeDelete(project, listOf(element), optionValues, ignoreConflicts = BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore())
        }
    }

    override fun tearDown() {
        try {
            SafeDeleteAction.headlessRunner = null
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
}
