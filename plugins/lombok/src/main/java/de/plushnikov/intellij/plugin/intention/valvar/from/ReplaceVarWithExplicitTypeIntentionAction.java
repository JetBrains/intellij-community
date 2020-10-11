package de.plushnikov.intellij.plugin.intention.valvar.from;

import com.intellij.psi.PsiVariable;

public class ReplaceVarWithExplicitTypeIntentionAction extends AbstractReplaceVariableWithExplicitTypeIntentionAction {

  public ReplaceVarWithExplicitTypeIntentionAction() throws ClassNotFoundException {
    super(Class.forName("lombok.var"));
  }

  @Override
  protected void executeAfterReplacing(PsiVariable psiVariable) {

  }
}
