// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.testFrameworks.AbstractExpectedPatterns;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class TestNGExpectedPatterns extends AbstractExpectedPatterns {
  private static final List<Pattern> PATTERNS = new ArrayList<Pattern>();

  private static final String[] PATTERN_STRINGS = new String[]{
    "expected same with:\\<(.*)\\> but was:\\<(.*)\\>",
    "expected:\\<(.*)\\> but was:\\<(.*)\\>",
    "expected \\[(.*)\\] but got \\[(.*)\\]",
    "expected not same with:\\<(.*)\\> but was same:\\<(.*)\\>",
    "expected \\[(.*)\\] but found \\[(.*)\\]",
    "\nexpected: .*?\"(.*)\"\n\\s*but: .*?\"(.*)\""
    };

  static {
    registerPatterns(PATTERN_STRINGS, PATTERNS);
  }

  public static ComparisonFailureData createExceptionNotification(String message) {
    return createExceptionNotification(message, PATTERNS);
  }
}