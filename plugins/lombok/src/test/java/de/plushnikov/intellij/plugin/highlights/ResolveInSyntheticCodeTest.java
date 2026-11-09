package de.plushnikov.intellij.plugin.highlights;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public final class ResolveInSyntheticCodeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/highlights");

  @Test
  public void testResolveInSyntheticMethod() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("Test.java", """
      @lombok.Data
      class C {
        final int a;
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiMethod constructor = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getConstructors()[0];
      PsiParameter parameter = constructor.getParameterList().getParameter(0);
      assertNotNull(parameter);
      PsiExpression expression =
        ((PsiAssignmentExpression)((PsiExpressionStatement)constructor.getBody().getStatements()[0]).getExpression()).getRExpression();
      var ref = assertInstanceOf(PsiReferenceExpression.class, expression);
      assertEquals(parameter, ref.resolve());
    });
  }
}
