package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DelegateMethodProcessor extends AbstractMethodProcessor {

  public DelegateMethodProcessor() {
    super(PsiMethod.class, LombokClassNames.DELEGATE, LombokClassNames.EXPERIMENTAL_DELEGATE);
  }

  private DelegateHandler getDelegateHandler() {
    return ApplicationManager.getApplication().getService(DelegateHandler.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiMethod.getParameterList().getParametersCount() > 0) {
      builder.addError(LombokBundle.message("inspection.message.delegate.legal.only.on.no.argument.methods"));
      result = false;
    }

    final PsiType returnType = psiMethod.getReturnType();
    final DelegateHandler handler = getDelegateHandler();
    result &= null != returnType && handler.validate(psiMethod, returnType, psiAnnotation, builder);

    return result;
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiType returnType = psiMethod.getReturnType();
    if (null != returnType) {
      final DelegateHandler handler = getDelegateHandler();
      handler.generateElements(psiMethod, returnType, psiAnnotation, target);
    }
  }
}
