package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import lombok.Delegate;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;

public class DelegateMethodProcessor extends AbstractMethodProcessor {

  private final DelegateHandler handler;

  public DelegateMethodProcessor() {
    this(Delegate.class);
  }

  protected DelegateMethodProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass) {
    super(supportedAnnotationClass, PsiMethod.class);
    handler = new DelegateHandler();
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiMethod.getParameterList().getParametersCount() > 0) {
      builder.addError("@Delegate is legal only on no-argument methods.");
      result = false;
    }

    final PsiType returnType = psiMethod.getReturnType();
    result &= null != returnType && handler.validate(psiMethod, returnType, psiAnnotation, builder);

    return result;
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiType returnType = psiMethod.getReturnType();
    if (null != returnType) {
      handler.generateElements(psiMethod, returnType, psiAnnotation, target);
    }
  }
}
