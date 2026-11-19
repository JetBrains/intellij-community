// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class TerminalOperationTest {
  private final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture();

  @Test
  public void testForEach() {
    traceFixture.get().doTest(true);
  }

  @Test
  public void testForEachOrdered() {
    traceFixture.get().doTest(true);
  }
}
