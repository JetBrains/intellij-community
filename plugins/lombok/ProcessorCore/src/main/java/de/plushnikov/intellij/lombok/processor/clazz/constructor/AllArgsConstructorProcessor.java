package de.plushnikov.intellij.lombok.processor.clazz.constructor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class AllArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  private static final String CLASS_NAME = AllArgsConstructor.class.getName();

  public AllArgsConstructorProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Collection<PsiField> allNotInitializedNotStaticFields = getAllNotInitializedAndNotStaticFields(psiClass);
    final String methodVisibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    if (null != methodVisibility) {
      target.addAll((Collection<? extends Psi>) createConstructorMethod(psiClass, methodVisibility, psiAnnotation, allNotInitializedNotStaticFields));
    }
  }

}
