// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.settings.ThreadsViewSettings
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences

abstract class AbstractFlowAsyncStackTraceTest : AbstractAsyncStackTraceTest() {

    override fun doMultiFileTest(
        files: TestFiles,
        preferences: DebuggerPreferences
    ) {
        XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowLibraryStackFrames = false
        setSettingForTest(
            { ThreadsViewSettings.getInstance().SHOW_LINE_NUMBER },
            { ThreadsViewSettings.getInstance().SHOW_LINE_NUMBER = it },
            false
        )
        setSettingForTest(
            { ThreadsViewSettings.getInstance().SHOW_CLASS_NAME },
            { ThreadsViewSettings.getInstance().SHOW_CLASS_NAME = it },
            false
        )
        doWhenXSessionPausedThenResume {
            printAsyncStackTrace()
        }
    }

    override fun collectFrames(session: XDebugSession?): List<XStackFrame> {
        return XDebuggerTestUtil.collectFrames(session ?: return emptyList())
    }

    override fun getFramePresentation(f: XStackFrame): String {
        return XDebuggerTestUtil.getFramePresentation(f)
    }
}