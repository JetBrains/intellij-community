// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.helper

import com.intellij.psi.PsiFile
import com.intellij.refactoring.modplan.ModPlanRefactoringListener
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.psi.KtFile

/**
 * Deletes the unused imports of a Kotlin file that a headless refactoring changed, as [KotlinOptimizeImportsRefactoringHelper] does after
 * a legacy one: all of them, also the ones that were unused before.
 */
internal class KotlinModPlanOptimizeImportsListener : ModPlanRefactoringListener {
    override fun onRefactoringDone(copy: PsiFile) {
        if (copy !is KtFile) return
        val unusedImports = KotlinOptimizeImportsFacility.getInstance().analyzeImports(copy)?.unusedImports ?: return
        for (directive in unusedImports) {
            if (directive.isValid) directive.delete()
        }
    }
}
