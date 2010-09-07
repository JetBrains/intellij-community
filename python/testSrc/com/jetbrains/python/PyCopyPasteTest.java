package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyCopyPasteTest extends PyLightFixtureTestCase {
  public void testIndent1() {
    doTest();
  }

  public void testIndent2() {
    doTest();
  }

  private void doTest() {
    String name = getTestName(false);
    myFixture.configureByFile("copyPaste/" + name + ".src.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
    myFixture.configureByFile("copyPaste/"+ name + ".dst.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResultByFile("copyPaste/" + name + ".after.py");
  }
}
