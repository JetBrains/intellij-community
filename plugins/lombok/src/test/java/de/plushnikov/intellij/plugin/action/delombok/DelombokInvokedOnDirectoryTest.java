package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokActionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokActionFixture;

@TestApplication
public class DelombokInvokedOnDirectoryTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/delombok/invokeOnDirectory", () -> new DelombokGetterAction());

  @Test
  public void testGetterClassFromProjectViewDirectory() {
    LightJavaCodeInsightTestFixture myFixture = lombokAction.get().getFixture();
    myFixture.configureByFile("/before" + myFixture.getTestName(false) + ".java");
    PsiFile psiFile = myFixture.getFile();

    lombokAction.get().performProjectViewActionTest(psiFile.getVirtualFile().getParent());
    lombokAction.get().waitForProjectViewActionProcessing(() -> psiFile.getText().contains("public String getName()"));
    myFixture.checkResultByFile("/after" + myFixture.getTestName(false) + ".java", true);
  }
}
