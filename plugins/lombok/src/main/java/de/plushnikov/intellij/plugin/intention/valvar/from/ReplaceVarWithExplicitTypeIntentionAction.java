package de.plushnikov.intellij.plugin.intention.valvar.from;

import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.LombokClassNames;

public class ReplaceVarWithExplicitTypeIntentionAction extends AbstractReplaceVariableWithExplicitTypeIntentionAction {

  public ReplaceVarWithExplicitTypeIntentionAction() {
    super(LombokClassNames.VAR);
  }

  @Override
  protected void executeAfterReplacing(PsiVariable psiVariable) {

  }
}
