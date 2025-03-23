// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.SuspendContextImpl
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferenceKeys
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences

abstract class AbstractIrKotlinSteppingTestWithVariablePrinting : AbstractIrKotlinSteppingTest() {
    private var isFrameTest = false

    override fun doOnBreakpoint(action: SuspendContextImpl.() -> Unit) {
        val wrappedAction: SuspendContextImpl.() -> Unit = {
            printAfterFrame(this)
            action()
        }

        super.doOnBreakpoint {
            if (!isFrameTest) {
                wrappedAction()
                return@doOnBreakpoint
            }
            printFrame(this, wrappedAction)
        }
    }

    override fun doMultiFileTest(
        files: TestFiles,
        preferences: DebuggerPreferences
    ) {
        isFrameTest = preferences[DebuggerPreferenceKeys.PRINT_FRAME]
        super.doMultiFileTest(files, preferences)
    }

    abstract fun printAfterFrame(context: SuspendContextImpl)
}
