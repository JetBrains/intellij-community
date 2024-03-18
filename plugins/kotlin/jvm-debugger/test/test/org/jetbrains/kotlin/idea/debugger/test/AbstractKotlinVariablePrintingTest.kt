// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.process.ProcessOutputTypes
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.FramePrinter

abstract class AbstractKotlinVariablePrintingTest : KotlinDescriptorTestCaseWithStepping() {
    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        for (i in 0..countBreakpointsNumber(files.wholeFile)) {
            doOnBreakpoint {
                printFrame()
            }
        }
    }

    private fun SuspendContextImpl.printFrame() =
        processStackFramesOnPooledThread {
            val out = FramePrinter(this@printFrame).printTopVariables(first())
            print(out, ProcessOutputTypes.SYSTEM)
            resume(this@printFrame)
        }
}

abstract class AbstractK1IdeK2CodeKotlinVariablePrintingTest : AbstractKotlinVariablePrintingTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}