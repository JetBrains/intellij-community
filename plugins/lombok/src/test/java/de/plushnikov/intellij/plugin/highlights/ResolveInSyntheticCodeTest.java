package de.plushnikov.intellij.plugin.highlights;

import com.intellij.psi.*;

public final class ResolveInSyntheticCodeTest extends AbstractLombokHighlightsTest {
  public void testResolveInSyntheticMethod() {
    myFixture.configureByText("Test.java", """
      @lombok.Data
      class C {
        final int a;
      }
      """);
    PsiMethod constructor = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getConstructors()[0];
    PsiParameter parameter = constructor.getParameterList().getParameter(0);
    assertNotNull(parameter);
    PsiExpression expression =
      ((PsiAssignmentExpression)((PsiExpressionStatement)constructor.getBody().getStatements()[0]).getExpression()).getRExpression();
    var ref = assertInstanceOf(expression, PsiReferenceExpression.class);
    assertEquals(parameter, ref.resolve());
  }
}
