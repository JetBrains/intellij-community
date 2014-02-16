package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import lombok.Delegate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DelegateMethodProcessor extends AbstractMethodProcessor {

  private final DelegateHandler handler;

  protected DelegateMethodProcessor() {
    super(Delegate.class, PsiMethod.class);
    handler = new DelegateHandler();
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    final PsiType returnType = psiMethod.getReturnType();
    return null != returnType && handler.validate(returnType, psiAnnotation, builder);
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiType returnType = psiMethod.getReturnType();
    if (null != returnType) {
      handler.generateElements(psiMethod, returnType, psiAnnotation, target);
    }
  }
}
