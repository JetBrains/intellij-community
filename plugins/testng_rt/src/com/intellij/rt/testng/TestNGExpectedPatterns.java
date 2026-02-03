// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.testFrameworks.AbstractExpectedPatterns;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TestNGExpectedPatterns extends AbstractExpectedPatterns {
  private static final Pattern SOFT_ASSERT_PATTERN = Pattern.compile("expected \\[(.*)] but found \\[(.*)]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern SOFT_ASSERT_CHAINED_PATTERN = Pattern.compile("but found \\[(.*)]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  private static final List<Pattern> PATTERNS = new ArrayList<>();

  private static final String[] PATTERN_STRINGS = new String[]{
    "expected same with:\\<(.*)\\> but was:\\<(.*)\\>",
    "expected:\\<(.*)\\> but was:\\<(.*)\\>",
    "expected \\[(.*)\\] but got \\[(.*)\\]",
    "expected not same with:\\<(.*)\\> but was same:\\<(.*)\\>",
    "expected \\[(.*)\\] but found \\[(.*)\\]",
    "\nexpected: .*?\"(.*)\"\n\\s*but: .*?\"(.*)\"",
    "assertion failed: expected (.*), found (.*)"
    };

  static {
    registerPatterns(PATTERN_STRINGS, PATTERNS);
  }

  public static ComparisonFailureData createExceptionNotification(String message) {
    if (exceedsMessageThreshold(message)) return null;
    ComparisonFailureData softAssertNotification = createExceptionNotification(message, SOFT_ASSERT_PATTERN);
    if (softAssertNotification != null) {
      return SOFT_ASSERT_CHAINED_PATTERN.matcher(softAssertNotification.getExpected()).find() ? null : softAssertNotification;
    }

    return createExceptionNotification(message, PATTERNS);
  }
}