package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyClassNameCompletionTest extends PyLightFixtureTestCase {
  private boolean myOldAutocompleteValue;

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/completion/className/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    myOldAutocompleteValue = codeInsightSettings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
    codeInsightSettings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = true;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = myOldAutocompleteValue;
    super.tearDown();
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testReuseExisting() throws Exception {
    doTest();
  }

  public void testQualified() throws Exception {
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

  private void doTest() throws Exception {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py");
    myFixture.complete(CompletionType.CLASS_NAME);
    myFixture.checkResultByFile(getTestName(true) + "/" + getTestName(true) + ".after.py");
  }
}
