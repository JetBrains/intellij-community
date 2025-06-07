// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.process.ProcessOutputTypes
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences

abstract class AbstractCoroutineDumpTest : KotlinDescriptorTestCaseWithStackFrames() {
    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        for (i in 0..countBreakpointsNumber(files.wholeFile)) {
            doOnBreakpoint {
                try {
                    printCoroutinesDump()
                } finally {
                    resume(this)
                }
            }
        }
    }

    private fun SuspendContextImpl.printCoroutinesDump() {
        val infoCache = CoroutineDebugProbesProxy(this).dumpCoroutines()
        if (!infoCache.isOk()) {
            throw AssertionError("Dump failed")
        }

        val states = infoCache.cache
        print(stringDump(states), ProcessOutputTypes.SYSTEM)
    }

    private fun stringDump(infoData: List<CoroutineInfoData>) = buildString {
        infoData.forEach {
            appendLine("\"${it.name}#${it.id}\", state: ${it.state}")
        }
    }
}

abstract class AbstractK1IdeK2CodeCoroutineDumpTest : AbstractCoroutineDumpTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
