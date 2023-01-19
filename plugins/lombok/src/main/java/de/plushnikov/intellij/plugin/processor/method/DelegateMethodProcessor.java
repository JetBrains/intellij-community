package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DelegateMethodProcessor extends AbstractMethodProcessor {

  public DelegateMethodProcessor() {
    super(PsiMethod.class, LombokClassNames.DELEGATE, LombokClassNames.EXPERIMENTAL_DELEGATE);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemSink problemSink) {
    boolean result = true;
    if (psiMethod.getParameterList().getParametersCount() > 0) {
      problemSink.addErrorMessage("inspection.message.delegate.legal.only.on.no.argument.methods");
      result = false;
    }

    final PsiType returnType = psiMethod.getReturnType();
    result &= null != returnType && DelegateHandler.validate(psiMethod, returnType, psiAnnotation, problemSink);

    return result;
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiType returnType = psiMethod.getReturnType();
    if (null != returnType) {
      DelegateHandler.generateElements(psiMethod, returnType, psiAnnotation, target);
    }
  }
}
