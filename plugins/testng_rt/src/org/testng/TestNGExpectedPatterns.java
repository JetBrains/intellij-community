/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.testFrameworks.AbstractExpectedPatterns;

import java.util.ArrayList;
import java.util.List;

class TestNGExpectedPatterns extends AbstractExpectedPatterns {
  private static final List PATTERNS = new ArrayList();

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