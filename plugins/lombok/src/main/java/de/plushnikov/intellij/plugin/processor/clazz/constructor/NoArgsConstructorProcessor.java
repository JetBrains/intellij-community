package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class NoArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  public NoArgsConstructorProcessor() {
    super(LombokClassNames.NO_ARGS_CONSTRUCTOR, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result;

    result = super.validate(psiAnnotation, psiClass, builder);

    final String staticConstructorName = getStaticConstructorName(psiAnnotation);
    result &= validateIsConstructorNotDefined(psiClass, staticConstructorName, Collections.emptyList(), builder);

    return result;
  }

  @NotNull
  public Collection<PsiMethod> createNoArgsConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation) {
    final boolean forceConstructorWithJavaDefaults = isForceConstructor(psiAnnotation);
    return createNoArgsConstructor(psiClass, methodVisibility, psiAnnotation, forceConstructorWithJavaDefaults);
  }

  @NotNull
  public Collection<PsiMethod> createNoArgsConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation, boolean withJavaDefaults) {
    final Collection<PsiField> params = getConstructorFields(psiClass, withJavaDefaults);
    return createConstructorMethod(psiClass, methodVisibility, psiAnnotation, withJavaDefaults, params);
  }

  private boolean isForceConstructor(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "force", false);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    if (null != methodVisibility) {
      target.addAll(createNoArgsConstructor(psiClass, methodVisibility, psiAnnotation));
    }
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {

      final boolean forceConstructorWithJavaDefaults = isForceConstructor(psiAnnotation);
      final Collection<PsiField> params = getConstructorFields(containingClass, forceConstructorWithJavaDefaults);

      if (PsiClassUtil.getNames(params).contains(psiField.getName())) {
        return LombokPsiElementUsage.WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }

  @NotNull
  private Collection<PsiField> getConstructorFields(PsiClass containingClass, boolean forceConstructorWithJavaDefaults) {
    Collection<PsiField> params;
    if (forceConstructorWithJavaDefaults) {
      params = getRequiredFields(containingClass);
    } else {
      params = Collections.emptyList();
    }
    return params;
  }
}
