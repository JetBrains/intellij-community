package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.intention.LombokIntentionActionTest;

import static com.intellij.psi.PsiModifier.FINAL;
import static de.plushnikov.intellij.plugin.intention.valvar.to.ValAndVarIntentionActionTest.EXPLICIT_TO_VAL_VAR_DIRECTORY;

public class ReplaceFinalExplicitTypeWithValIntentionActionTest extends LombokIntentionActionTest {

  @Override
  protected String getBasePath() {
    return EXPLICIT_TO_VAL_VAR_DIRECTORY;
  }

  @Override
  public IntentionAction getIntentionAction() {
    return new ReplaceExplicitTypeWithValIntentionAction();
  }

  @Override
  public boolean wasInvocationSuccessful() {
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(elementAtCaret, PsiLocalVariable.class);
    if (localVariable == null) {
      return false;
    }
    PsiModifierList modifierList = localVariable.getModifierList();
    PsiTypeElement typeElement = localVariable.getTypeElement();
    return typeElement.getText().equals("val") && localVariable.hasModifierProperty(FINAL) && modifierList != null && !modifierList.hasExplicitModifier(FINAL);
  }

  public void testFinalTypeWithVal() {
    doTest();
  }
}
