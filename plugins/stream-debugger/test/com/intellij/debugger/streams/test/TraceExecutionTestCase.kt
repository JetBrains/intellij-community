// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.debugger.DebuggerTestCase
import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.DebuggerPositionResolver
import com.intellij.debugger.streams.core.psi.impl.DebuggerPositionResolverImpl
import com.intellij.debugger.streams.core.testFramework.ChainSelector
import com.intellij.debugger.streams.core.testFramework.ChainSelector.Companion.byIndex
import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupportProvider
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Producer
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * @author Vitaliy.Bibaev
 */
@SkipSlowTestLocally
abstract class TraceExecutionTestCase : DebuggerTestCase() {
  @JvmField
  protected val LOG: Logger = Logger.getInstance(javaClass)

  @JvmField
  protected val myPositionResolver: DebuggerPositionResolver = DebuggerPositionResolverImpl()

  override fun initOutputChecker(): OutputChecker {
    return object : OutputChecker(Producer { testAppPath }, Producer { appOutputPath }) {
      override fun replaceAdditionalInOutput(str: String?): String {
        return this@TraceExecutionTestCase.replaceAdditionalInOutput(super.replaceAdditionalInOutput(str))
      }
    }
  }

  override fun setUpModule() {
    super.setUpModule()
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_16)
  }

  protected open fun replaceAdditionalInOutput(str: String): String {
    return str
  }

  override fun getTestAppPath(): String {
    return File(PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/debug/").absolutePath
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      super.tearDown()
    }
    catch (t: Throwable) {
      if (!t.message!!.startsWith("Thread leaked: Thread[")) {
        throw t
      }
    }
  }

  protected fun doTest(isResultNull: Boolean) {
    val className = getTestName(false)
    doTest(isResultNull, className, DEFAULT_CHAIN_SELECTOR)
  }

  protected fun doTest(isResultNull: Boolean, className: String) {
    doTest(isResultNull, className, DEFAULT_CHAIN_SELECTOR)
  }

  protected fun doTest(isResultNull: Boolean, className: String, chainSelector: ChainSelector = DEFAULT_CHAIN_SELECTOR) {
    try {
      doTestImpl(isResultNull, className, chainSelector)
    }
    catch (e: Exception) {
      throw AssertionError("exception thrown", e)
    }
  }

  @Throws(ExecutionException::class)
  private fun doTestImpl(isResultNull: Boolean, className: String, chainSelector: ChainSelector) {
    val testName = getTestName(false)
    LOG.info("Test started: " + testName)
    runInEdtAndWait {
      runBlockingWithFlushing(testName, 30.seconds) {
        withContext(Dispatchers.Default) {
          createLocalProcess(className)
          val session = debuggerSession.getXDebugSession()
          assertNotNull(session)

          val completed = AtomicBoolean(false)

          val helper = getHelper(session!!)

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
                    helper.onPause(chainSelector, isResultNull)
                  }
                }
              }
              catch (t: Throwable) {
                println("Exception caught: " + t + ", " + t.message, ProcessOutputTypes.SYSTEM)

                t.printStackTrace()

                resume()
              }
            }

            fun resume() {
              ApplicationManager.getApplication().invokeLater(Runnable { session.resume() })
            }
          }, getTestRootDisposable())
        }
      }
    }
  }

  protected open fun getHelper(session: XDebugSession): TraceExecutionTestHelper {
    return ExecutionTestCaseHelper(this, session, getLibrarySupportProvider(), myPositionResolver, LOG)
  }

  protected open fun getLibrarySupportProvider(): LibrarySupportProvider {
    return StandardLibrarySupportProvider()
  }

  companion object {
    private val DEFAULT_CHAIN_SELECTOR = byIndex(0)
  }
}

