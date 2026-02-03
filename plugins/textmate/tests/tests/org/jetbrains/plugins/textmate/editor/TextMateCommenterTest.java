package org.jetbrains.plugins.textmate.editor;

import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.plugins.textmate.TextMateAcceptanceTestCase;

public class TextMateCommenterTest extends TextMateAcceptanceTestCase {

  public void testLatexLineCommenter() {
    doLineTest("tex");
  }

  public void testPhpLineCommenter() {
    doLineTest("php_hack");
  }

  public void testBatLineCommenter() {
    doLineTest("bat_hack");
  }

  public void testJsxBlockCommenter() {
    doBlockTest("jsx_hack");
  }

  public void testBlockCommenter() {
    doBlockTest("php_hack");
  }

  public void testBlockCommenterInInjection() {
    doBlockTest("php_hack");
  }

  public void testBlockCommenterInInjection_2() {
    doBlockTest("php_hack");
  }

  public void testCommenterInInjectedCode() {
    doBlockTest("php_hack");
  }

  private void doLineTest(String extension) {
    doTest(extension, IdeActions.ACTION_COMMENT_LINE);
  }

  private void doBlockTest(String extension) {
    doTest(extension, IdeActions.ACTION_COMMENT_BLOCK);
  }

  private void doTest(final String extension, final String actionId) {
    myFixture.configureByFile(getTestName(true) + "." + extension);
    myFixture.performEditorAction(actionId);
    myFixture.checkResultByFile(getTestName(true) + "_after." + extension);
  }


  @Override
  protected String getTestPath() {
    return "/editor/data";
  }
}
