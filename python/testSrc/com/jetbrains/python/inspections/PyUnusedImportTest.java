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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyUnusedImportTest extends PyTestCase {
  // PY-3626
  public void testModuleAndSubmodule() {
    doTest("py3626.py");
  }

  // PY-3201
  public void testSubpackageInInitPy() {
    doTest("package1/__init__.py");
  }

  // PY-5589
  public void testUnusedPackageAndSubmodule() {
    doTest("test1.py");
  }

  // PY-5621
  public void testUnusedSubmodule() {
    doTest("test1.py");
  }

  // PY-6380
  public void testUnusedAfterStarImport() {
    doTest("test1.py");
  }

  // PY-10667
  public void testUsedLastImport() {
    doTest("test1.py");
  }

  //PY-20075
  public void testMultipleSubmodules() {
    doTest("test1.py");
  }

  private void doTest(@NotNull String filename) {
    myFixture.copyDirectoryToProject("inspections/unusedImport/" + getTestName(true), "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, filename);
  }
}
