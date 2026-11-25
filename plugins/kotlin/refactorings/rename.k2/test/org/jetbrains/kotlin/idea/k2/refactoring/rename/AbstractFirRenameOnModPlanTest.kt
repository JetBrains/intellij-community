// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.rename.ModPlanJavaRenameOptions
import com.intellij.testFramework.ModPlanRenameTestSupport
import org.jetbrains.kotlin.idea.refactoring.rename.AutomaticOverloadsRenamer

/**
 * The tests of the legacy rename of Kotlin, run on the ModPlan engine. A test that fails here is a difference of the ModPlan rename from the
 * legacy one; the known ones are in [KNOWN_GAPS], by their reason, and such a test fails once the gap is gone.
 */
abstract class AbstractFirRenameOnModPlanTest : AbstractFirRenameTest() {
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

/** Renames [substitution] on the ModPlan engine as `runRenameProcessor` of the legacy tests renames it, with every automatic renamer. */
internal fun runRenameOnModPlan(
    project: Project,
    newName: String,
    substitution: PsiElement?,
    renameParamsObject: JsonObject,
    isSearchInComments: Boolean,
    isSearchTextOccurrences: Boolean
) {
    if (substitution == null) return
    if (renameParamsObject["overloadRenamer.onlyPrimaryElement"]?.asBoolean == true) {
        with(AutomaticOverloadsRenamer) { substitution.elementFilter = { false } }
    }
    // As the legacy test, which gives the substituted element to RenameProcessor: no question about the base method.
    val options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, isSearchInComments)
        .with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, isSearchTextOccurrences)
        .with(ModPlanJavaRenameOptions.RENAME_BASE_METHOD, false)
    ModPlanRenameTestSupport.renameOnModPlan(project, substitution, newName, ModPlanRenameTestSupport.withEveryRenamer(options))
}

private val KNOWN_GAPS: Map<String, List<String>> = mapOf(
    "The unused imports of a file of a related rename: the legacy rename keeps them, as it optimizes only the files of the usages. The gap goes with the legacy rename" to listOf(
        "AutomaticRenamerKotlinTestClassForFacade_AutomaticRenamerKotlinTestClassForFacade",
    ),
    "Not analyzed yet: the text differs, or an edit fails" to listOf(
    ),
)
