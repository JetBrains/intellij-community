package com.jetbrains.rest;

import com.intellij.psi.PsiElement;
import com.jetbrains.rest.fixtures.RestFixtureTestCase;

/**
 * User : catherine
 */
public class RestGotoDeclarationTest extends RestFixtureTestCase {

  public void testFootnote() {
    doTest("[1]");
  }

  public void testCitation() {
    doTest("[CIT01]");
  }

  public void testHyperlink() {
    doTest("_Reddit:");
  }

  public void testEscapedHyperlink() {
    doTest("_Reddit and digg:");
  }

  public void testSubstitution() {
    doTest("|text|");
  }

  public void testAnonimousHyperlink() {
    doTest("__");
  }

  public void testTitle() {
    doTest("=============\nTitle\n=============\n");
  }

  public void testInsideFootnote() {
    doTest("_Reddit:");
  }

  public void testEmpty() {
    RestGotoProvider provider = new RestGotoProvider();
    final String path = "/goto/empty.rst";
    myFixture.configureByFile(path);
    int e = myFixture.getCaretOffset();
    PsiElement result = provider.getGotoDeclarationTarget(myFixture.getFile().findElementAt(e), myFixture.getEditor());
    assertNull(result);
  }

  private void doTest(String expected) {
    RestGotoProvider provider = new RestGotoProvider();
    final String path = "/goto/" + getTestName(true);
    myFixture.configureByFile(path + ".rst");

    int e = myFixture.getCaretOffset();
    PsiElement result = provider.getGotoDeclarationTarget(myFixture.getFile().findElementAt(e), myFixture.getEditor());
    assertNotNull(result);
    assertEquals(expected, result.getText());
  }
}
