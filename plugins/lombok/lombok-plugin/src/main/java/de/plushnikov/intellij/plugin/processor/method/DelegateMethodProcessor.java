package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.field.DelegateFieldProcessor;
import lombok.Delegate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DelegateMethodProcessor extends AbstractMethodProcessor {

  private DelegateFieldProcessor fieldProcessor = new DelegateFieldProcessor();

  protected DelegateMethodProcessor() {
    super(Delegate.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected void processIntern(PsiMethod psiMethod, PsiAnnotation psiAnnotation, List<? super PsiElement> target) {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
