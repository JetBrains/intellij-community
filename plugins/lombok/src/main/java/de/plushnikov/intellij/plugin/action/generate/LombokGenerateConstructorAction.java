package de.plushnikov.intellij.plugin.action.generate;

import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;

/**
 * Action for constructor generation to bypass lombok defined constructors
 */
public class LombokGenerateConstructorAction extends BaseGenerateAction {
  public LombokGenerateConstructorAction() {
    super(new LombokGenerateConstructorHandler());
  }

  @Override
  protected boolean isValidForClass(final PsiClass targetClass) {
    return super.isValidForClass(targetClass) && !(targetClass instanceof PsiAnonymousClass);
  }
}
