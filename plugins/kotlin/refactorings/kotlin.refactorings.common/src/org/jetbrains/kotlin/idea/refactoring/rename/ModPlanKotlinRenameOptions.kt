// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.refactoring.modplan.ModPlanOption
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

/**
 * The options of the Kotlin rename, besides those of [com.intellij.refactoring.modplan.rename.ModPlanRenameOptions].
 */
@ApiStatus.Experimental
object ModPlanKotlinRenameOptions {
    /**
     * The answer of the question "the declaration overrides some, rename the base declarations instead?": on, the base declarations are
     * renamed with their hierarchy; off, the declaration is renamed alone.
     */
    @JvmField
    val RENAME_BASE_DECLARATION: ModPlanOption<Boolean> = ModPlanOption.bool(
        "renameBaseDeclaration",
        true,
        KotlinBundle.messagePointer("button.rename.base"),
    )

    /** The answer of the question "rename the other accessor of the property as well?", asked for a getter or a setter. */
    @JvmField
    val RENAME_OTHER_ACCESSOR: ModPlanOption<Boolean> = ModPlanOption.bool(
        "renameOtherAccessor",
        true,
        KotlinBundle.messagePointer("rename.other.accessor"),
    )
}
