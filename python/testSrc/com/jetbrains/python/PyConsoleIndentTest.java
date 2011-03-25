package com.jetbrains.python;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.console.PyConsoleIndentUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public class PyConsoleIndentTest extends UsefulTestCase{
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

  private void doTest() {
    String name = getTestName(true);
    try {
      String fileText = FileUtil.loadFile(new File(getTestDataPath() + name + ".py"));
      String expected = FileUtil.loadFile(new File(getTestDataPath() + name + ".after.py"));
      assertEquals(expected, PyConsoleIndentUtil.normalize(fileText));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/console/";
  }
}
