// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractInplaceRenameTest

/**
 * The tests of the inline rename of Kotlin, with the rename of members on the ModPlan engine. The known differences from the legacy rename
 * are in [KNOWN_GAPS], by their reason, and such a test fails once the gap is gone.
 */
abstract class AbstractK2InplaceRenameOnModPlanTest : AbstractInplaceRenameTest() {
    override fun setUp() {
        super.setUp()
        Registry.get("refactoring.rename.modPlan").setValue(true, testRootDisposable)
    }

    override fun doTest(unused: String) {
        if (getTestName(false).removePrefix("test") !in KNOWN_GAPS.values.flatten()) {
            super.doTest(unused)
            return
        }
        val failure = runCatching { super.doTest(unused) }.exceptionOrNull()
        assertNotNull("The known gap is gone: remove ${getTestName(false)} from KNOWN_GAPS", failure)
    }
}

private val KNOWN_GAPS: Map<String, List<String>> = emptyMap()
