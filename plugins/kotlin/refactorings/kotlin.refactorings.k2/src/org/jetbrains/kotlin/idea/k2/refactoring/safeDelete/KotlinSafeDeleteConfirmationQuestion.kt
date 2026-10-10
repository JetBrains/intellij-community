// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.openapi.project.Project
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * A yes-or-no question of the interactive safe delete that shows the message dialog of the legacy flow, such as whether to delete the
 * expected declaration together with the actual ones; the answer is [option].
 *
 * @param confirm shows the dialog: true for yes, false for no
 */
internal class KotlinSafeDeleteConfirmationQuestion(
    private val option: ModPlanOption<Boolean>,
    private val confirm: (Project) -> Boolean,
) : ModPlanQuestion.Custom {
    override fun options(): List<ModPlanOption<*>> = listOf(option)

    @RequiresEdt
    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues {
        return currentValues.with(option, confirm(project))
    }
}
