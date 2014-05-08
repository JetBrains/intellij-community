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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.console.PyConsoleIndentUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public class PyConsoleIndentTest extends UsefulTestCase {
  public void testIndent1() {
    doTest();
  }

  public void testIndent2() {
    doTest();
  }

  public void testIndent3() {
    doTest();
  }

  public void testIndent4() {
    doTest();
  }

  public void testIndent5() {
    doTest();
  }

  public void testIndent6() {
    doTest();
  }

  public void testIndent7() {
    doTest(2);
  }

  public void testIndent8() {
    doTest();
  }

  public void testIndent9() {
    doTest();
  }

  public void testIndent10() {
    doTest();
  }

  public void testIndent11() {
    doTest();
  }

  public void testIndent12() {
    doTest();
  }

  public void testIndent13() {
    doTest();
  }

  public void testIndent14() {
    doTest();
  }

  public void testIndent15() {
    doTest();
  }

  public void testIndent16() {
    doTest();
  }

  private void doTest() {
    doTest(0);
  }

  private void doTest(int indent) {
    String name = getTestName(true);
    try {
      String fileText = FileUtil.loadFile(new File(getTestDataPath() + name + ".py"));
      String expected = FileUtil.loadFile(new File(getTestDataPath() + name + ".after.py"));
      assertEquals(StringUtil.convertLineSeparators(expected), StringUtil.convertLineSeparators(
        PyConsoleIndentUtil.normalize(fileText, indent)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/console/";
  }
}

