package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokInvokedOnDirectoryTest extends LombokLightActionTestCase {
  @Override
  protected AnAction getAction() {
    return new DelombokGetterAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/invokeOnDirectory";
  }

  public void testGetterClassFromProjectViewDirectory() {
    myFixture.configureByFile("/before" + getTestName(false) + ".java");
    PsiFile psiFile = myFixture.getFile();

    performProjectViewActionTest(psiFile.getVirtualFile().getParent());
    waitForProjectViewActionProcessing(() -> psiFile.getText().contains("public String getName()"));
    myFixture.checkResultByFile("/after" + getTestName(false) + ".java", true);
  }
}
