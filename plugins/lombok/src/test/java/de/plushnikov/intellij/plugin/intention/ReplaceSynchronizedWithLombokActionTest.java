package de.plushnikov.intellij.plugin.intention;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokIntentionFixture;

@TestApplication
public class ReplaceSynchronizedWithLombokActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokIntentionFixture> intention =
    lombokIntentionFixture(projectFixture, "/intention/synchronized", () -> new ReplaceSynchronizedWithLombokAction(),
                           this::wasInvocationSuccessful);

  private boolean wasInvocationSuccessful() {
    LightJavaCodeInsightTestFixture myFixture = intention.get().getFixture();
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class);
    if (psiMethod == null) {
      return false;
    }

    return psiMethod.hasAnnotation(LombokClassNames.SYNCHRONIZED) && !psiMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED);
  }

  @Test
  public void testJavaSynchronizedMethod() {
    intention.get().doTest();
  }

  @Test
  public void testJavaSynchronizedStaticMethod() {
    intention.get().doTest();
  }
}
