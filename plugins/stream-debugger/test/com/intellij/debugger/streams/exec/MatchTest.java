// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;

@TestApplication
public class MatchTest {
  private final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture();

  @Test
  public void testAllMatchExtraCalls() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testAnyMatchExtraCalls() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testNoneMatchExtraCalls() {
    traceFixture.get().doTest(false);
  }
}
