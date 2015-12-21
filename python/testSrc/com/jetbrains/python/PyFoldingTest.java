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

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyFoldingTest extends PyTestCase {
  private void doTest() {
    myFixture.testFolding(getTestDataPath() + "/folding/" + getTestName(true) + ".py");
  }

  public void testClassTrailingSpace() {  // PY-2544
    doTest();
  }

  public void testDocString() {
    doTest();
  }

  public void testCustomFolding() {
    doTest();
  }

  public void testImportBlock() {
    doTest();
  }

  public void testBlocksFolding() {
    doTest();
  }

  public void testLongStringsFolding() {
    doTest();
  }

  public void testCollectionsFolding() {
    doTest();
  }

  public void testMultilineComments() {
    doTest();
  }

}
