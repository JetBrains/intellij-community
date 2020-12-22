package de.plushnikov.intellij.plugin.intention.valvar.from;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiVariable;

public class ReplaceValWithExplicitTypeIntentionAction extends AbstractReplaceVariableWithExplicitTypeIntentionAction {

  public ReplaceValWithExplicitTypeIntentionAction() {
    super(lombok.val.class);
  }

  @Override
  protected void executeAfterReplacing(PsiVariable psiVariable) {
    PsiModifierList psiModifierList = psiVariable.getModifierList();
    if (psiModifierList != null) {
      psiModifierList.setModifierProperty(PsiModifier.FINAL, true);
    }
  }
}
