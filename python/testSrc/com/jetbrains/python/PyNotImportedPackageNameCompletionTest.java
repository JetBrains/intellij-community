// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher.QualifiedNameMatcher;

import java.util.List;

public class PyNotImportedPackageNameCompletionTest extends PyTestCase {
  public void testDotAfterPackageName() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(true) + "/main.after.py");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion/notImportedPackageName/";
  }
}
