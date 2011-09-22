package de.plushnikov.intellij.lombok.processor.clazz.constructor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class NoArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  private static final String CLASS_NAME = NoArgsConstructor.class.getName();

  public NoArgsConstructorProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    if (null != methodVisibility) {
      target.addAll((Collection<? extends Psi>) createConstructorMethod(psiClass, methodVisibility, psiAnnotation, Collections.<PsiField>emptyList()));
    }
  }

}
