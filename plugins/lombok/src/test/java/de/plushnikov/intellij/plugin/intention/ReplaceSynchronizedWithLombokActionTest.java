package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;

public class ReplaceSynchronizedWithLombokActionTest extends LombokIntentionActionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/synchronized";
  }

  @Override
  public ModCommandAction getAction() {
    return new ReplaceSynchronizedWithLombokAction();
  }

  @Override
  public boolean wasInvocationSuccessful() {
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class);
    if (psiMethod == null) {
      return false;
    }

    return psiMethod.hasAnnotation(LombokClassNames.SYNCHRONIZED) && !psiMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED);
  }

  public void testJavaSynchronizedMethod() {
    doTest();
  }

  public void testJavaSynchronizedStaticMethod() {
    doTest();
  }
}
