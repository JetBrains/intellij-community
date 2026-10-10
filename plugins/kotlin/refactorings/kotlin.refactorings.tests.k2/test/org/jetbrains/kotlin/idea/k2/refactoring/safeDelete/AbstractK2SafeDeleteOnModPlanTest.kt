// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.openapi.util.registry.Registry
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow

/**
 * The data of [AbstractK2SafeDeleteTest], run with the interactive flow on the ModPlan engine. A test that fails here is a difference of the
 * ModPlan flow from the legacy one.
 */
abstract class AbstractK2SafeDeleteOnModPlanTest : AbstractK2SafeDeleteTest() {
    override fun setUp() {
        super.setUp()
        Registry.get("refactoring.safeDelete.modPlan").setValue(true, testRootDisposable)
        ModPlanSafeDeleteFlow.failOnLegacyInTests(testRootDisposable)
    }
}
