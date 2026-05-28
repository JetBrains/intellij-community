// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test.sequence.exec

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.impl.DebuggerPositionResolverImpl
import com.intellij.debugger.streams.core.testFramework.ChainSelector
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.xdebugger.XDebugSessionListener
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluator
import org.jetbrains.kotlin.idea.debugger.test.KotlinDescriptorTestCaseWithStepping
import org.jetbrains.kotlin.idea.debugger.test.TestFiles
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import java.util.concurrent.atomic.AtomicBoolean

abstract class KotlinTraceTestCase : KotlinDescriptorTestCaseWithStepping() {
    private companion object {
        val DEFAULT_CHAIN_SELECTOR = ChainSelector.byIndex(0)
    }

    abstract val librarySupportProvider: LibrarySupportProvider

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        // Sequence expressions are verbose. Disable expression logging for sequence debugger
        KotlinEvaluator.LOG_COMPILATIONS = false

        val session = debuggerSession.xDebugSession ?: kotlin.test.fail("XDebugSession is null")
        assertNotNull(session)

        val completed = AtomicBoolean(false)
        val helper = ExecutionTestCaseHelper(this, session, librarySupportProvider, DebuggerPositionResolverImpl(), LOG)

        session.addSessionListener(object : XDebugSessionListener {
            override fun sessionPaused() {
                if (completed.getAndSet(true)) {
                    resume()
                    return
                }
                try {
                    printContext(debugProcess.debuggerContext)
                    runInEdt {
                        runBlocking {
                            helper.onPause(DEFAULT_CHAIN_SELECTOR)
                        }
                    }
                } catch (t: Throwable) {
                    println("Exception caught: $t, ${t.message}", ProcessOutputTypes.SYSTEM)
                    t.printStackTrace()

                    resume()
                }

            }

            private fun resume() {
                ApplicationManager.getApplication().invokeLater { session.resume() }
            }
        }, testRootDisposable)
    }
}