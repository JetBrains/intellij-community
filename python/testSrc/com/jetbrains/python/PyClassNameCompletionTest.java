package com.jetbrains.python;

import com.intellij.codeInsight.completion.CompletionType;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyClassNameCompletionTest extends PyTestCase {

  public void testSimple() {
    doTest();
  }

  public void testReuseExisting() {
    doTest();
  }

  public void testQualified() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldValue = settings.PREFER_FROM_IMPORT;
    settings.PREFER_FROM_IMPORT = false;
    try {
      doTest();
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldValue;
    }
  }

  public void testFunction() {
    doTest();
  }

  private void doTest() {
    final String path = "/completion/className/" + getTestName(true);
    myFixture.copyDirectoryToProject(path, "");
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.checkResultByFile(path + "/" + getTestName(true) + ".after.py");
  }
}
