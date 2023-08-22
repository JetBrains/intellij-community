// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyLightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;


public class PyBinaryModuleCompletionTest extends PyTestCase {
  public void testPySideImport() {  // PY-2443
    myFixture.configureByFile("completion/pySideImport.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/pySideImport.after.py");
  }

  public void testPyQt4Import() {
    myFixture.configureByFile("completion/pyQt4Import.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/pyQt4Import.after.py");
  }

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourDescriptor;
  }

  private static final PyLightProjectDescriptor ourDescriptor = new PyLightProjectDescriptor("MockSdkWithBinaryModules");
}
