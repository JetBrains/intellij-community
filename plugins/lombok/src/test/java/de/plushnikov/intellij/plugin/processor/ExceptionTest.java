package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Plushnikov Michail
 */
@TestApplication
public class ExceptionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/augment/exception");

  @Test
  public void testError526() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = LombokTestUtil.loadToPsiFile(myFixture, myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      assertNotNull(psiFile);

      PsiClass psiClass = PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.getCaretOffset()), PsiClass.class);
      assertNotNull(psiClass);

      // call augmentprovider first time
      final PsiMethod[] psiClassMethods = psiClass.getMethods();
      assertEquals(8, psiClassMethods.length);

      // change something to trigger cache drop
      WriteCommandAction.writeCommandAction(myFixture.getProject(), psiFile).compute(() ->
        {
          psiClass.getModifierList().addAnnotation("java.lang.SuppressWarnings");
          return true;
        }
      );

      // call augment provider second time
      final PsiMethod[] psiClassMethods2 = psiClass.getMethods();
      assertArrayEquals(psiClassMethods, psiClassMethods2);
    });
  }
}
