// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;

public class PyNotImportedPackageNameCompletionTest extends PyTestCase {
  public void testPackageNameBasicCompletion() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  public void testPackageNameNotSuggestedForAssignmentTarget() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    assertDoesntContain(myFixture.getLookupElementStrings(), "foobar");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion/notImportedPackageName/";
  }
}
