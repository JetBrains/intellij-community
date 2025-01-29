// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.test;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.impl.OutputChecker;
import com.intellij.debugger.streams.lib.LibrarySupportProvider;
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupportProvider;
import com.intellij.debugger.streams.psi.DebuggerPositionResolver;
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Vitaliy.Bibaev
 */
@SkipSlowTestLocally
public abstract class TraceExecutionTestCase extends DebuggerTestCase {
  private static final ChainSelector DEFAULT_CHAIN_SELECTOR = ChainSelector.byIndex(0);
  private static final LibrarySupportProvider DEFAULT_LIBRARY_SUPPORT_PROVIDER = new StandardLibrarySupportProvider();
  protected final Logger LOG = Logger.getInstance(getClass());
  protected final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();

  @Override
  protected OutputChecker initOutputChecker() {
    return new OutputChecker(() -> getTestAppPath(), () -> getAppOutputPath()) {
      @Override
      protected String replaceAdditionalInOutput(String str) {
        return TraceExecutionTestCase.this.replaceAdditionalInOutput(super.replaceAdditionalInOutput(str));
      }
    };
  }

  @NotNull
  protected String replaceAdditionalInOutput(@NotNull String str) {
    return str;
  }

  protected LibrarySupportProvider getLibrarySupportProvider() {
    return DEFAULT_LIBRARY_SUPPORT_PROVIDER;
  }

  @Override
  protected String getTestAppPath() {
    return new File(PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/debug/").getAbsolutePath();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      //noinspection SuperTearDownInFinally
      super.tearDown();
    }
    catch (Throwable t) {
      if (!t.getMessage().startsWith("Thread leaked: Thread[")) {
        throw t;
      }
    }
  }

  protected void doTest(boolean isResultNull) {
    doTest(isResultNull, DEFAULT_CHAIN_SELECTOR);
  }

  protected void doTest(boolean isResultNull, @NotNull String className) {
    doTest(isResultNull, className, DEFAULT_CHAIN_SELECTOR);
  }

  protected void doTest(boolean isResultNull, @NotNull ChainSelector chainSelector) {
    final String className = getTestName(false);
    doTest(isResultNull, className, chainSelector);
  }

  protected void doTest(boolean isResultNull, @NotNull String className, @NotNull ChainSelector chainSelector) {
    try {
      doTestImpl(isResultNull, className, chainSelector);
    }
    catch (Exception e) {
      throw new AssertionError("exception thrown", e);
    }
  }

  private void doTestImpl(boolean isResultNull, @NotNull String className, @NotNull ChainSelector chainSelector)
    throws ExecutionException {
    LOG.info("Test started: " + getTestName(false));
    createLocalProcess(className);
    final XDebugSession session = getDebuggerSession().getXDebugSession();
    assertNotNull(session);

    final AtomicBoolean completed = new AtomicBoolean(false);

    final TraceExecutionTestHelper helper = getHelper(session);

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        if (completed.getAndSet(true)) {
          resume();
          return;
        }
        try {
          printContext(getDebugProcess().getDebuggerContext());
          helper.onPause(chainSelector, isResultNull);
        }
        catch (Throwable t) {
          println("Exception caught: " + t + ", " + t.getMessage(), ProcessOutputTypes.SYSTEM);

          //noinspection CallToPrintStackTrace
          t.printStackTrace();

          resume();
        }
      }

      private void resume() {
        ApplicationManager.getApplication().invokeLater(session::resume);
      }
    }, getTestRootDisposable());
  }

  protected @NotNull TraceExecutionTestHelper getHelper(XDebugSession session) {
    return new JavaTraceExecutionTestHelper(session, getLibrarySupportProvider(), myPositionResolver, LOG);
  }

  protected class JavaTraceExecutionTestHelper extends TraceExecutionTestHelper {
    private final XDebugSession mySession;

    public JavaTraceExecutionTestHelper(@NotNull XDebugSession session,
                                        @NotNull LibrarySupportProvider myLibrarySupportProvider,
                                        @NotNull DebuggerPositionResolver myDebuggerPositionResolver,
                                        @NotNull Logger LOG) {

      super(session, myLibrarySupportProvider, myDebuggerPositionResolver, LOG);
      mySession = session;
    }



    @Override
    protected @NotNull String getTestName() {
      return TraceExecutionTestCase.this.getTestName(false);
    }

    @Override
    public void print(@NotNull String message, @NotNull Key processOutputType) {
      TraceExecutionTestCase.this.print(message, processOutputType);
    }

    @Override
    public void println(@NotNull String message, @NotNull Key processOutputType) {
      TraceExecutionTestCase.this.println(message, processOutputType);
    }

    @Override
    public void resume() {
      ApplicationManager.getApplication().invokeLater(mySession::resume);
    }
  }
}
