package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.intention.LombokIntentionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokIntentionFixture;
import static de.plushnikov.intellij.plugin.intention.valvar.to.ValAndVarIntentionActionTest.EXPLICIT_TO_VAL_VAR_DIRECTORY;

@TestApplication
public class ReplaceExplicitTypeWithVarIntentionActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR);
  private final TestFixture<LombokIntentionFixture> intention =
    lombokIntentionFixture(projectFixture, EXPLICIT_TO_VAL_VAR_DIRECTORY, () -> new ReplaceExplicitTypeWithVarIntentionAction(),
                           this::wasInvocationSuccessful);

  private boolean wasInvocationSuccessful() {
    LightJavaCodeInsightTestFixture myFixture = intention.get().getFixture();
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(elementAtCaret, PsiLocalVariable.class);
    if (localVariable == null) {
      return false;
    }
    PsiTypeElement typeElement = localVariable.getTypeElement();
    return typeElement.getText().equals("var");
  }

  @Test
  public void testTypeWithVar() {
    intention.get().doTest();
  }
}
