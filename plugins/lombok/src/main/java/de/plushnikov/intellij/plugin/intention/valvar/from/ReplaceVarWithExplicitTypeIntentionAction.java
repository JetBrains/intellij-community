package de.plushnikov.intellij.plugin.intention.valvar.from;

import com.intellij.psi.PsiVariable;

public class ReplaceVarWithExplicitTypeIntentionAction extends AbstractReplaceVariableWithExplicitTypeIntentionAction {

  public ReplaceVarWithExplicitTypeIntentionAction() {
    super(lombok.var.class);
  }

  @Override
  protected void executeAfterReplacing(PsiVariable psiVariable) {

  }
}
