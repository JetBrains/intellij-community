// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.debugger.test.AbstractAsyncStackTraceTest
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.TestFiles
import org.jetbrains.kotlin.idea.k2.debugger.test.K2DebuggerTestCompilerFacility

abstract class AbstractK2AsyncStackTraceTest : AbstractAsyncStackTraceTest() {
    override val isK2Plugin: Boolean get() = true

    override fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles,
        jvmTarget: JvmTarget,
        useIrBackend: Boolean,
        lambdasGenerationScheme: JvmClosureGenerationScheme,
    ): DebuggerTestCompilerFacility {
        return K2DebuggerTestCompilerFacility(project, testFiles, jvmTarget, useIrBackend, lambdasGenerationScheme)
    }
}