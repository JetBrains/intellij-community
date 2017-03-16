package com.intellij.debugger.streams.exec;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.impl.OutputChecker;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.testFramework.IdeaTestUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceExecutionTestCase extends DebuggerTestCase {
  public void testSimple() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest();
  }

  @Override
  protected OutputChecker initOutputChecker() {
    return new OutputChecker(getTestAppPath(), getAppOutputPath());
  }

  @Override
  protected String getTestAppPath() {
    return new File("testData/" + getRelativeTestPath()).getAbsolutePath();
  }

  protected List<TraceInfo> doTest() throws InterruptedException, ExecutionException, InvocationTargetException {
    final String name = getTestName(false);
    createLocalProcess(name);
    doWhenXSessionPausedThenResume(() -> {
      printContext(getDebugProcess().getDebuggerContext());
      getDebuggerSession().getXDebugSession();
      IdeaTestUtil.invokeNamedAction("AdvancedStreamTracerAction");
    });
    return Collections.emptyList();
  }

  protected String getRelativeTestPath() {
    return "debug";
  }
}
