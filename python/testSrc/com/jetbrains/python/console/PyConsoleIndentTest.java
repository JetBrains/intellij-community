// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.PythonTestUtil;

import java.io.File;
import java.io.IOException;

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

