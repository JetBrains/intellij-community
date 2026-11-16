// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * The tests of the legacy rename of Kotlin in several modules, run on the ModPlan engine. The known differences are in [KNOWN_GAPS], by
 * their reason, and such a test fails once the gap is gone.
 */
abstract class AbstractFirMultiModuleRenameOnModPlanTest : AbstractFirMultiModuleRenameTest() {
    override fun doTest(path: String) {
        if (getTestName(false).removePrefix("test") !in KNOWN_GAPS.values.flatten()) {
            super.doTest(path)
            return
        }
        val failure = runCatching { super.doTest(path) }.exceptionOrNull()
        assertNotNull("The known gap is gone: remove ${getTestName(false)} from KNOWN_GAPS", failure)
    }

    override fun runRename(
        project: Project,
        newName: String,
        substitution: PsiElement?,
        renameParamsObject: JsonObject,
        isSearchInComments: Boolean,
        isSearchTextOccurrences: Boolean
    ) {
        runRenameOnModPlan(project, newName, substitution, renameParamsObject, isSearchInComments, isSearchTextOccurrences)
    }
}

private val KNOWN_GAPS: Map<String, List<String>> = mapOf(
)
