package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public final class RequiredArgsConstructorProcessor extends AbstractConstructorClassProcessor {
  public RequiredArgsConstructorProcessor() {
    super(LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    boolean result;

    result = super.validate(psiAnnotation, psiClass, builder);

    final Collection<PsiField> allReqFields = getRequiredFields(psiClass);
    final String staticConstructorName = getStaticConstructorName(psiAnnotation);
    result &= validateIsConstructorNotDefined(psiClass, staticConstructorName, allReqFields, builder);

    return result;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target,
                                     @Nullable String nameHint) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    if (null != methodVisibility) {
      target.addAll(
        createRequiredArgsConstructor(psiClass, methodVisibility, psiAnnotation, getStaticConstructorName(psiAnnotation), false));
    }
  }

  public @NotNull Collection<PsiMethod> createRequiredArgsConstructor(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, @Nullable String staticName, boolean skipConstructorIfAnyConstructorExists) {
    final Collection<PsiField> allReqFields = getRequiredFields(psiClass);

    return createConstructorMethod(psiClass, methodModifier, psiAnnotation, false, allReqFields, staticName, skipConstructorIfAnyConstructorExists);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final boolean classAnnotatedWithValue = PsiAnnotationSearchUtil.isAnnotatedWith(containingClass, LombokClassNames.VALUE);
      if (isRequiredField(psiField, false, classAnnotatedWithValue)) {
        return LombokPsiElementUsage.WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
