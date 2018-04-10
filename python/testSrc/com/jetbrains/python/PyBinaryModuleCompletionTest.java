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

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyLightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
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

  private static final PyLightProjectDescriptor ourDescriptor = new PyLightProjectDescriptor("WithBinaryModules");
}
