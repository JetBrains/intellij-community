// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.debugger.DebuggerTestFixture
import com.intellij.debugger.DebuggerTestOptions
import com.intellij.debugger.debuggerTestFixture
import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.DebuggerPositionResolver
import com.intellij.debugger.streams.core.psi.impl.DebuggerPositionResolverImpl
import com.intellij.debugger.streams.core.testFramework.ChainSelector
import com.intellij.debugger.streams.core.testFramework.ChainSelector.Companion.byIndex
import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupportProvider
import com.intellij.execution.ExecutionTestOptions
import com.intellij.execution.OutputCheckerFactory
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ThrowableRunnable
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.function.UnaryOperator
import kotlin.io.path.Path
import kotlin.io.path.pathString

/** Creates the helper that traces the selected stream chain when the debuggee stops. */
fun interface TraceHelperFactory {
  fun create(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper
}

/**
 * Creates the fixture of the stream trace tests: a debugger fixture on a test app in the `testData` directory of the plugin.
 *
 * The main module has the language level [languageLevel].
 * With [libraryJarPath], the main module and the debuggee get this library, and the output check replaces its path with `!LIBRARY_JAR!`.
 * Then [replaceAdditionalInOutput] changes the output before the check.
 * When the debuggee stops, [helper] creates the helper that traces the selected chain with [librarySupportProvider].
 * Declare it as an instance field of a `@TestApplication` class.
 *
 * @param testAppRelativePath the directory of the test app in the `testData` directory of the plugin.
 */
@JvmOverloads
fun traceExecutionFixture(
  helper: TraceHelperFactory = TraceHelperFactory(::ExecutionTestCaseHelper),
  replaceAdditionalInOutput: UnaryOperator<String> = UnaryOperator.identity(),
  testAppRelativePath: String = "debug",
  languageLevel: LanguageLevel = LanguageLevel.JDK_24,
  librarySupportProvider: LibrarySupportProvider = StandardLibrarySupportProvider(),
  libraryJarPath: String? = null,
): TestFixture<TraceExecutionFixture> = testFixture("traceExecutionFixture") { context ->
  val outputChecker = OutputCheckerFactory { appPath, outputPath ->
    object : OutputChecker(Supplier { appPath }, Supplier { outputPath }) {
      override fun replaceAdditionalInOutput(str: String?): String {
        var result: String = super.replaceAdditionalInOutput(str)
        if (libraryJarPath != null) {
          result = result.replaceLibraryPath(libraryJarPath)
        }
        return replaceAdditionalInOutput.apply(result)
      }
    }
  }
  val execution = ExecutionTestOptions()
    .withSetUpModule { module ->
      IdeaTestUtil.setModuleLanguageLevel(module, languageLevel)
      if (libraryJarPath != null) {
        VfsRootAccess.allowRootAccess(module, Path(libraryJarPath).parent.pathString)
        PsiTestUtil.addLibrary(module, libraryJarPath)
      }
    }
    .withUpdateJavaParameters { parameters ->
      if (libraryJarPath != null) {
        parameters.classPath.add(libraryJarPath)
      }
    }
  val testAppPath = StreamDebuggerTestUtil.getTestDataPath(testAppRelativePath)
  val debugger = debuggerTestFixture(testAppPath, outputChecker, DebuggerTestOptions().withExecution(execution)).init()
  val fixture = TraceExecutionFixture(
    debugger = debugger,
    testMethodName = context.extensionContext.requiredTestMethod.name,
    librarySupportProvider = librarySupportProvider,
    LOG = Logger.getInstance(context.extensionContext.requiredTestClass),
    helperFactory = helper,
  )
  initialized(fixture) {}
}

private fun String.replaceLibraryPath(libraryPath: String): String {
  val caseSensitive = SystemInfo.isFileSystemCaseSensitive
  val result = StringUtil.replace(this, FileUtil.toSystemDependentName(libraryPath), "!LIBRARY_JAR!", !caseSensitive)
  return StringUtil.replace(result, FileUtil.toSystemIndependentName(libraryPath), "!LIBRARY_JAR!", !caseSensitive)
}

/** The state and the helpers of a test that [traceExecutionFixture] creates. */
class TraceExecutionFixture internal constructor(
  /** The debugger fixture of the test. */
  val debugger: DebuggerTestFixture,
  private val testMethodName: String,
  /** The library support of the traced chains. */
  val librarySupportProvider: LibrarySupportProvider,
  /** The logger of the test class. */
  @Suppress("PropertyName") val LOG: Logger,
  private val helperFactory: TraceHelperFactory,
) {
  /** The position resolver of the traced chains. */
  val positionResolver: DebuggerPositionResolver = DebuggerPositionResolverImpl()

  /** The name of the test method without the `test` prefix, with the first letter in upper case. */
  val testName: String
    get() = PlatformTestUtil.getTestName(testMethodName, false)

  /** Runs [doTest] for the main class with the name of the test. */
  fun doTest(isResultNull: Boolean) {
    doTest(isResultNull, testName, DEFAULT_CHAIN_SELECTOR)
  }

  /**
   * Runs the test in [DebuggerTestFixture.runTest].
   * It starts the debuggee [className], and when the debuggee stops, it traces the chain of [chainSelector].
   * The trace result goes to the output, and [DebuggerTestFixture.runTest] compares it with the `.out` file.
   */
  @JvmOverloads
  fun doTest(isResultNull: Boolean, className: String, chainSelector: ChainSelector = DEFAULT_CHAIN_SELECTOR) {
    debugger.runTest(ThrowableRunnable {
      try {
        doTestImpl(isResultNull, className, chainSelector)
      }
      catch (e: Exception) {
        throw AssertionError("exception thrown", e)
      }
    })
  }

  private fun doTestImpl(isResultNull: Boolean, className: String, chainSelector: ChainSelector) {
    LOG.info("Test started: $testName")
    debugger.createLocalProcess(className)
    val session = debugger.debuggerSession!!.xDebugSession
    assertNotNull(session)

    val completed = AtomicBoolean(false)

    val helper = helperFactory.create(this, session!!)

    session.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        if (completed.getAndSet(true)) {
          resume()
          return
        }
        try {
          debugger.printContext(debugger.debugProcess!!.debuggerContext)
          runInEdt {
            runBlocking {
              helper.onPause(chainSelector, isResultNull)
            }
          }
        }
        catch (t: Throwable) {
          debugger.println("Exception caught: " + t + ", " + t.message, ProcessOutputTypes.SYSTEM)

          t.printStackTrace()

          resume()
        }
      }

      fun resume() {
        ApplicationManager.getApplication().invokeLater(Runnable { session.resume() })
      }
    }, debugger.disposable)
  }

  private companion object {
    private val DEFAULT_CHAIN_SELECTOR = byIndex(0)
  }
}
