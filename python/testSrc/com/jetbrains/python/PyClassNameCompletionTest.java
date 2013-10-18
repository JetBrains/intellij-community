package com.jetbrains.python;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
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
    doTestWithoutFromImport();
  }

  public void testFunction() {
    doTest();
  }

  public void testModule() {
    doTest();
  }

  public void testVariable() {
    doTest();
  }

  public void testSubmodule() {  // PY-7887
    doTest();
  }

  public void testSubmoduleRegularImport() {  // PY-7887
    doTestWithoutFromImport();
  }

  public void testStringLiteral() { // PY-10526
    doTest();
  }
  private void doTestWithoutFromImport() {
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

  private void doTest() {
    final String path = "/completion/className/" + getTestName(true);
    myFixture.copyDirectoryToProject(path, "");
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py");
    myFixture.complete(CompletionType.BASIC, 2);
    if (myFixture.getLookupElements() != null) {
      myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    }
    myFixture.checkResultByFile(path + "/" + getTestName(true) + ".after.py", true);
  }
}
