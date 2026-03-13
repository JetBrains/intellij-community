// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

abstract class AbstractK1IrBreakpointHighlightingTest : AbstractIrBreakpointHighlightingTest() {

    override fun getMainClassName(compilerFacility: DebuggerTestCompilerFacility): String {
        return K1DebuggerTestCompilerFacility.analyzeAndFindMainClass(project, sourcesKtFiles.jvmKtFiles) ?: error("No main class found")
    }
}