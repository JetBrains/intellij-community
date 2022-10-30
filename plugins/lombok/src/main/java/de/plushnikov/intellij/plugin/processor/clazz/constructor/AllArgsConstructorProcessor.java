package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public final class AllArgsConstructorProcessor extends AbstractConstructorClassProcessor {
  public AllArgsConstructorProcessor() {
    super(LombokClassNames.ALL_ARGS_CONSTRUCTOR, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    boolean result;

    result = super.validate(psiAnnotation, psiClass, builder);

    final Collection<PsiField> allNotInitializedNotStaticFields = getAllFields(psiClass);
    final String staticConstructorName = getStaticConstructorName(psiAnnotation);
    result &= validateIsConstructorNotDefined(psiClass, staticConstructorName, allNotInitializedNotStaticFields, builder);

    return result;
  }

  @NotNull
  public Collection<PsiMethod> createAllArgsConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation) {
    final Collection<PsiField> allNotInitializedNotStaticFields = getAllNotInitializedAndNotStaticFields(psiClass);
    return createConstructorMethod(psiClass, methodVisibility, psiAnnotation, false, allNotInitializedNotStaticFields);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    if (null != methodVisibility) {
      final String staticConstructorName = getStaticConstructorName(psiAnnotation);
      target.addAll(createAllArgsConstructor(psiClass, methodVisibility, psiAnnotation, staticConstructorName));
    }
  }

  @NotNull
  private Collection<PsiMethod> createAllArgsConstructor(PsiClass psiClass, String methodVisibility, PsiAnnotation psiAnnotation, String staticName) {
    final Collection<PsiField> allNotInitializedNotStaticFields = getAllFields(psiClass);
    return createAllArgsConstructor(psiClass, methodVisibility, psiAnnotation, staticName, allNotInitializedNotStaticFields);
  }

  @NotNull
  public Collection<PsiMethod> createAllArgsConstructor(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, String staticName, Collection<PsiField> allNotInitializedNotStaticFields) {
    return createAllArgsConstructor(psiClass, methodModifier, psiAnnotation, staticName, allNotInitializedNotStaticFields, false);
  }

  @NotNull
  public Collection<PsiMethod> createAllArgsConstructor(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, String staticName, Collection<PsiField> allNotInitializedNotStaticFields, boolean skipConstructorIfAnyConstructorExists) {
    return createConstructorMethod(psiClass, methodModifier, psiAnnotation, false, allNotInitializedNotStaticFields, staticName, skipConstructorIfAnyConstructorExists);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(getAllNotInitializedAndNotStaticFields(containingClass)).contains(psiField.getName())) {
        return LombokPsiElementUsage.WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
