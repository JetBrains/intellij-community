/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User : ktisha
 */
public class PythonDoctestCompletionTest extends PyTestCase {

  private void doDoctestTest(String expected) {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    final LookupElement[] elements = myFixture.completeBasic();
    if (elements != null) {
      for (LookupElement lookup : elements) {
        System.out.println(lookup.getLookupString());
        if (lookup.getLookupString().equals(expected))
          return;
      }
    }
    fail();
  }

  public void testForInDoctest() {
    doDoctestTest("for");
  }

  public void testImportInDoctest() {
    doDoctestTest("foo");
  }

  public void testFunctionInDoctest() {
    doDoctestTest("foo");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion/doctest";
  }
}
