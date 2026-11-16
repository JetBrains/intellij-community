// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.openapi.util.registry.Registry

/** The tests of the conflicts of the inline rename of Kotlin, with the rename of members on the ModPlan engine. */
abstract class AbstractK2InplaceRenameConflictOnModPlanTest : AbstractK2InplaceRenameConflictTest() {
    override fun setUp() {
        super.setUp()
        Registry.get("refactoring.rename.modPlan").setValue(true, testRootDisposable)
    }
}
