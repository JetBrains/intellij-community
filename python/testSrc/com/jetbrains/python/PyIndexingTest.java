/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;

import java.util.Collection;

/**
 * @author vlan
 */
public class PyIndexingTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "indexing/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
    myFixture.configureFromTempProjectFile("a.py");
  }

  public void testModuleNameIndex() {
    final Collection<String> modules = PyModuleNameIndex.getAllKeys(myFixture.getProject());
    assertContainsElements(modules, "ModuleNameIndex_foo");
    assertContainsElements(modules, "ModuleNameIndex_bar");
    assertDoesntContain(modules, "__init__");
    assertDoesntContain(modules, "ModuleNameIndex_baz");
  }

  // PY-19047
  public void testPy19047() {
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, new Throwable());
  }
}
