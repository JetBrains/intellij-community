package de.plushnikov.intellij.plugin.action.intellij;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class RenameClassActionTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/intellij";
  }

  protected void doTest(String newName) throws Exception {
    myFixture.configureByFile("/before" + getTestName(false) + ".java");

    PsiElement psiElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiElement psiClass = PsiTreeUtil.getContextOfType(psiElement, PsiClass.class, true);
    myFixture.renameElement(psiClass, newName);

    myFixture.checkResultByFile("/after" + getTestName(false) + ".java", true);
  }

  public void testLogClassRenamed() throws Exception {
    doTest("CakeCooked");
  }

  public void testConstructors() throws Exception {
    doTest("MyBaseClass1");
  }
}
