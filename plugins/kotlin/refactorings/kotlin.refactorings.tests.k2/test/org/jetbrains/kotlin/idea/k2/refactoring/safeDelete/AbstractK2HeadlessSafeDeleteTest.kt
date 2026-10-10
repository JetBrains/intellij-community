// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.testFramework.SafeDeleteTestSupport
import com.intellij.testFramework.SafeDeleteTestSupport.withAll

/**
 * Runs the data of [AbstractK2SafeDeleteTest] through [SafeDeleteTestSupport.headlessSafeDelete], as the MCP tool runs it, with the same
 * directives: non-code usages never change the code, so the interactive expectations hold.
 */
abstract class AbstractK2HeadlessSafeDeleteTest : AbstractK2SafeDeleteTest() {
    override fun performSafeDelete(
        elements: List<PsiElement>,
        answers: Map<ModPlanOption<*>, Any>,
        ignoreConflicts: Boolean,
        nonCodeUsagesRecorder: SafeDeleteTestSupport.NonCodeUsageRecorder,
    ) {
        SafeDeleteTestSupport.headlessSafeDelete(project, elements, ModPlanOptionValues.EMPTY.withAll(answers), ignoreConflicts, nonCodeUsagesRecorder)
    }

    override fun performRefusedSafeDelete(elements: List<PsiElement>, answers: Map<ModPlanOption<*>, Any>, expectedReason: String) {
        val plan = SafeDeleteTestSupport.headlessSafeDelete(project, elements, ModPlanOptionValues.EMPTY.withAll(answers))
        assertEquals(expectedReason, (plan as? ModPlan.RefusedPlan)?.reason?.name)
    }
}
