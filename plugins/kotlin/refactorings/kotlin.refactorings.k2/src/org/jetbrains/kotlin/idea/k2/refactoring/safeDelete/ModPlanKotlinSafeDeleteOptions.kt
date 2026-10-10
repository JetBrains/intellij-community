// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.refactoring.modplan.ModPlanOption
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

/**
 * The options of the Kotlin safe delete, besides those of [com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions].
 */
@ApiStatus.Experimental
object ModPlanKotlinSafeDeleteOptions {
    /** The answer of the question "delete the expected declaration together with all the related actual ones?". */
    @JvmField
    val DELETE_EXPECT_ACTUAL: ModPlanOption<Boolean> = ModPlanOption.bool(
        "kotlinDeleteExpectActual",
        true,
        KotlinBundle.messagePointer("do.you.want.to.delete.expected.declaration.together.with.all.related.actual.ones"),
    )

    /**
     * The general answer of the question "the declaration overrides some, delete the base declarations instead?" for every deleted
     * declaration: true deletes them, false keeps them. Only a request sets it; the dialog answers with [SUPER_DECLARATIONS_OF]. An explicit
     * value answers the question of every declaration. The keys of [SUPER_DECLARATIONS_OF] narrow a true; with no keys and no explicit
     * value, the declaration is deleted alone.
     */
    @JvmField
    val DELETE_SUPER_DECLARATIONS: ModPlanOption<Boolean> = ModPlanOption.bool(
        "kotlinDeleteSuperDeclarations",
        false,
        KotlinBundle.messagePointer("delete.super.declarations"),
    )

    /**
     * The declarations whose base declarations are deleted, one [declarationKey] per line: a declaration of a list that names only other
     * declarations keeps its base declarations. The answers of the dialogs add up to this list, with the prefix `-` for an asked one that
     * is not chosen. [DELETE_SUPER_DECLARATIONS] false keeps them anyway.
     */
    @JvmField
    val SUPER_DECLARATIONS_OF: ModPlanOption<String> = ModPlanOption.text(
        "kotlinSuperDeclarationsOf",
        "",
        KotlinBundle.messagePointer("delete.super.declarations"),
    )
}
