// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener

abstract class AbstractK1IrKotlinEvaluateExpressionTest : AbstractIrKotlinEvaluateExpressionWithIRFragmentCompilerTest() {
    override fun getMainClassName(compilerFacility: DebuggerTestCompilerFacility): String {
        return super.getMainClassName(compilerFacility).also {
            KotlinCodeBlockModificationListener.Companion.getInstance(project).incModificationCount()
        }
    }
}