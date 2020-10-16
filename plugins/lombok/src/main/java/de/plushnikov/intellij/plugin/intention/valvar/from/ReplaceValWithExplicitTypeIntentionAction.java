package de.plushnikov.intellij.plugin.intention.valvar.from;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.LombokClassNames;

public class ReplaceValWithExplicitTypeIntentionAction extends AbstractReplaceVariableWithExplicitTypeIntentionAction {

  public ReplaceValWithExplicitTypeIntentionAction() {
    super(LombokClassNames.VAL);
  }

  @Override
  protected void executeAfterReplacing(PsiVariable psiVariable) {
    PsiModifierList psiModifierList = psiVariable.getModifierList();
    if (psiModifierList != null) {
      psiModifierList.setModifierProperty(PsiModifier.FINAL, true);
    }
  }
}
