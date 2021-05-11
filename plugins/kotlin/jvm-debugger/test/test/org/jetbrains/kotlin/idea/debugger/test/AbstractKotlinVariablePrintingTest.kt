package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.process.ProcessOutputTypes
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
        processStackFrameOnPooledThread {
            val out = FramePrinter(this@printFrame).printTopVariables(this)
            print(out, ProcessOutputTypes.SYSTEM)
            resume(this@printFrame)
        }
}
