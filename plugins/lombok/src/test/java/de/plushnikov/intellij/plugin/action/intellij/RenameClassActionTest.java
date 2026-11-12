package de.plushnikov.intellij.plugin.action.intellij;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@TestApplication
public class RenameClassActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/action/intellij");

  protected void doTest(String newName) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/before" + myFixture.getTestName(false) + ".java");

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElement psiElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      PsiElement psiClass = PsiTreeUtil.getContextOfType(psiElement, PsiClass.class, true);
      myFixture.renameElement(psiClass, newName);

      myFixture.checkResultByFile("/after" + myFixture.getTestName(false) + ".java", true);
    });
  }

  @Test
  public void testLogClassRenamed() throws Exception {
    doTest("CakeCooked");
  }

  @Test
  public void testConstructors() throws Exception {
    doTest("MyBaseClass1");
  }
}
