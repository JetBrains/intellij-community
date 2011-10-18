package com.jetbrains.python;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyWrapTest extends PyTestCase {
  private boolean myOldWrap;
  private int myOldMargin;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    myOldWrap = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    myOldMargin = settings.RIGHT_MARGIN;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    settings.RIGHT_MARGIN = 80;
  }

  @Override
  protected void tearDown() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myOldWrap;
    settings.RIGHT_MARGIN = myOldMargin;
    super.tearDown();
  }

  public void testBackslashOnWrap() {
    doTest("and hasattr(old_node, attr):");
  }

  public void testWrapInComment() {
    doTest("Aquitani");
  }

  public void testWrapInDocstring() {
    doTest("Aquitani");
  }

  public void testWrapInArgumentList() {
    doTest("=None");
  }

  private void doTest(final String textToType) {
    myFixture.configureByFile("wrap/" + getTestName(false) + ".py");
    myFixture.type(textToType);
    myFixture.checkResultByFile("wrap/" + getTestName(false) + ".after.py", true);
  }
}
