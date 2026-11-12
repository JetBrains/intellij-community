package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.intention.LombokIntentionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.PsiModifier.FINAL;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokIntentionFixture;
import static de.plushnikov.intellij.plugin.intention.valvar.to.ValAndVarIntentionActionTest.EXPLICIT_TO_VAL_VAR_DIRECTORY;

@TestApplication
public class ReplaceFinalExplicitTypeWithValIntentionActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR);
  private final TestFixture<LombokIntentionFixture> intention =
    lombokIntentionFixture(projectFixture, EXPLICIT_TO_VAL_VAR_DIRECTORY, () -> new ReplaceExplicitTypeWithValIntentionAction(),
                           this::wasInvocationSuccessful);

  private boolean wasInvocationSuccessful() {
    LightJavaCodeInsightTestFixture myFixture = intention.get().getFixture();
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(elementAtCaret, PsiLocalVariable.class);
    if (localVariable == null) {
      return false;
    }
    PsiModifierList modifierList = localVariable.getModifierList();
    PsiTypeElement typeElement = localVariable.getTypeElement();
    return typeElement.getText().equals("val") && localVariable.hasModifierProperty(FINAL) && modifierList != null && !modifierList.hasExplicitModifier(FINAL);
  }

  @Test
  public void testFinalTypeWithVal() {
    intention.get().doTest();
  }
}
