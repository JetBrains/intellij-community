// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class AccessibilityTest {
  protected final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testAccessToPrivateMethodsInStaticContext() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAccessToPrivateMethods() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAccessToPrivateClassInStaticContext() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAccessToPrivateClass() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAccessToPrivateClassWithMethodReference() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testNotImportedLambdaResult() {
    traceFixture.get().doTest(false);
  }

  protected @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session);
  }
}
