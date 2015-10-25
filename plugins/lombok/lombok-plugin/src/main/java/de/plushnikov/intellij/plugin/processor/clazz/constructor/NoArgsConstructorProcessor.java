package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class NoArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  public NoArgsConstructorProcessor() {
    super(NoArgsConstructor.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result;

    result = super.validate(psiAnnotation, psiClass, builder);

    final String staticConstructorName = getStaticConstructorName(psiAnnotation);
    if (!validateIsConstructorDefined(psiClass, staticConstructorName, Collections.<PsiField>emptyList(), builder)) {
      result = false;
    }
    return result;
  }

  @NotNull
  public Collection<PsiMethod> createNoArgsConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation) {
    final Collection<PsiField> params;
    final boolean forceConstructorWithJavaDefaults = isForceConstructor(psiAnnotation);
    if (forceConstructorWithJavaDefaults) {
      params = getRequiredFields(psiClass);
    } else {
      params = Collections.<PsiField>emptyList();
    }
    return createConstructorMethod(psiClass, methodVisibility, psiAnnotation, forceConstructorWithJavaDefaults, params);
  }

  protected boolean isForceConstructor(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "force", false);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    if (null != methodVisibility) {
      target.addAll(createNoArgsConstructor(psiClass, methodVisibility, psiAnnotation));
    }
  }
}
