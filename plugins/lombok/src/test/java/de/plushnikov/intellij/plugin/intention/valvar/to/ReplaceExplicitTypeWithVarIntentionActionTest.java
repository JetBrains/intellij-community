package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.intention.LombokIntentionActionTest;

import static de.plushnikov.intellij.plugin.intention.valvar.to.ValAndVarIntentionActionTest.EXPLICIT_TO_VAL_VAR_DIRECTORY;

public class ReplaceExplicitTypeWithVarIntentionActionTest extends LombokIntentionActionTest {

  @Override
  protected String getBasePath() {
    return EXPLICIT_TO_VAL_VAR_DIRECTORY;
  }

  @Override
  public IntentionAction getIntentionAction() {
    return new ReplaceExplicitTypeWithVarIntentionAction();
  }

  @Override
  public boolean wasInvocationSuccessful() {
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(elementAtCaret, PsiLocalVariable.class);
    if (localVariable == null) {
      return false;
    }
    PsiTypeElement typeElement = localVariable.getTypeElement();
    return typeElement.getText().equals("var");
  }

  public void testTypeWithVar() {
    doTest();
  }
}
