// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class ResultMappingTest {
  protected final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testToArrayMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveToArrayMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testCollectorToListMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testToListMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAnyMatchMappingTrue() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAnyMatchMappingFalse() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveAnyMatchMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAllMatchMappingTrue() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAllMatchMappingFalse() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveAllMatchMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testNoneMatchMappingTrue() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testNoneMatchMappingFalse() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveNoneMatchMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testFindFirstMappingPresent() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testFindFirstMappingNone() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveFindFirstMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testFindAnyMappingPresent() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testFindAnyMappingNone() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveFindAnyMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMaxMappingPresent() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMaxMappingNone() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveMaxMapping() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMinMappingPresent() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMinMappingNone() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveMinMapping() {
    traceFixture.get().doTest(false);
  }

  protected @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session);
  }
}
