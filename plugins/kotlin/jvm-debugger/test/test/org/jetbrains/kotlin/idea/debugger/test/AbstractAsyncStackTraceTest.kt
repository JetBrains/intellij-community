// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.settings.ThreadsViewSettings
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl

abstract class AbstractAsyncStackTraceTest : KotlinDescriptorTestCaseWithStepping() {

    override fun setUp() {
        super.setUp()
        DebuggerSettings.getInstance().INSTRUMENTING_AGENT = true

        restoreSettingAfterTest(
            XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings::isShowLibraryStackFrames,
            XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings::setShowLibraryStackFrames,
        )
    }

    override fun jarRepositories(): List<RemoteRepositoryDescription> {
        return super.jarRepositories() + intellijDepsRepository
    }
}
