// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences

abstract class AbstractCoroutineAsyncStackTraceTest : AbstractAsyncStackTraceTest() {

    override fun doMultiFileTest(
        files: TestFiles,
        preferences: DebuggerPreferences
    ) {
        XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowLibraryStackFrames = false
        doWhenXSessionPausedThenResume {
            printAsyncStackTrace(true)
        }
    }

    override fun collectFrames(session: XDebugSession?): List<XStackFrame> {
        return XDebuggerTestUtil.collectFrames(session ?: return emptyList())
    }

    override fun getFramePresentation(f: XStackFrame): String {
        return XDebuggerTestUtil.getFramePresentation(f)
    }
}

abstract class AbstractK2CoroutineAsyncStackTraceTest : AbstractCoroutineAsyncStackTraceTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2
}
