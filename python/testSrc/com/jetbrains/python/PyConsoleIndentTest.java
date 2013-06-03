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

