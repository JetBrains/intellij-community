// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener

abstract class AbstractIrKotlinEvaluateExpressionWithIRFragmentCompilerTest : AbstractIrKotlinEvaluateExpressionTest() {
    override fun getMainClassName(compilerFacility: DebuggerTestCompilerFacility): String {
        return super.getMainClassName(compilerFacility).also {
            KotlinCodeBlockModificationListener.getInstance(project).incModificationCount()
        }
    }
}

abstract class AbstractK1IdeK2CodeKotlinEvaluateExpressionTest : AbstractIrKotlinEvaluateExpressionWithIRFragmentCompilerTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
