// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestNGExpectedPatternsTest {

  @Test
  public void testExpectedButFound() {
    ComparisonFailureData failure = createNotification("expected [foo] but found [bar]");

    assertNotNull(failure);
    assertEquals("foo", failure.getExpected());
    assertEquals("bar", failure.getActual());
  }

  private static ComparisonFailureData createNotification(String message) {
    return TestNGExpectedPatterns.createExceptionNotification(message);
  }
}
