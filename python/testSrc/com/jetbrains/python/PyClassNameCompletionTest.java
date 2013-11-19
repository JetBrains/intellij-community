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

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyClassNameCompletionTest extends PyTestCase {

  public void testSimple() {
    doTest();
  }

  public void testReuseExisting() {
    doTest();
  }

  public void testQualified() {
    doTestWithoutFromImport();
  }

  public void testFunction() {
    doTest();
  }

  public void testModule() {
    doTest();
  }

  public void testVariable() {
    doTest();
  }

  public void testSubmodule() {  // PY-7887
    doTest();
  }

  public void testSubmoduleRegularImport() {  // PY-7887
    doTestWithoutFromImport();
  }

  public void testStringLiteral() { // PY-10526
    doTest();
  }
  private void doTestWithoutFromImport() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldValue = settings.PREFER_FROM_IMPORT;
    settings.PREFER_FROM_IMPORT = false;
    try {
      doTest();
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldValue;
    }
  }

  private void doTest() {
    final String path = "/completion/className/" + getTestName(true);
    myFixture.copyDirectoryToProject(path, "");
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py");
    myFixture.complete(CompletionType.BASIC, 2);
    if (myFixture.getLookupElements() != null) {
      myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    }
    myFixture.checkResultByFile(path + "/" + getTestName(true) + ".after.py", true);
  }
}
