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
    myFixture.configureByFile(getBasePath() + "/before" + getTestName(false) + ".java");

    PsiElement psiElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiElement psiClass = PsiTreeUtil.getContextOfType(psiElement, PsiClass.class, true);
    myFixture.renameElement(psiClass, newName);

    checkResultByFile(getBasePath() + "/after" + getTestName(false) + ".java");
  }

  public void testLogClassRenamed() throws Exception {
    doTest("CakeCooked");
  }
}
