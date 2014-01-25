package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class BaseDelombokAction extends BaseGenerateAction {

  protected BaseDelombokAction(BaseDelombokHandler handler) {
    super(handler);
  }

  @Override
  protected boolean isValidForClass(@NotNull PsiClass targetClass) {
    if (super.isValidForClass(targetClass)) {
      Collection<PsiAnnotation> psiAnnotations = ((BaseDelombokHandler) getHandler()).collectProccessableAnnotations(targetClass);
      return !psiAnnotations.isEmpty();
    }
    return false;
  }
}
