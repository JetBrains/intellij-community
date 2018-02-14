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
package com.jetbrains.python.pyi;

import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.*;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
@TestDataPath("$CONTENT_ROOT/../testData/pyi/parsing")
public class PyiParsingTest extends ParsingTestCase {
  public PyiParsingTest() {
    super("pyi/parsing", "pyi", new PyiParserDefinition(), new PythonParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    PythonDialectsTokenSetProvider.reset();
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  private void doTest() {
    doTest(true);
  }

  public void testSimple() {
    doTest();
    assertInstanceOf(myFile, PyiFile.class);
    final PyiFile pyiFile = (PyiFile)myFile;
    assertEquals(LanguageLevel.PYTHON37, pyiFile.getLanguageLevel());
  }
}
