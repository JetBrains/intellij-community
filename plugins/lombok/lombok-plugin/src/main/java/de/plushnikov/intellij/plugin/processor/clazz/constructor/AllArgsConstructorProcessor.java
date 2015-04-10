package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class AllArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  public AllArgsConstructorProcessor() {
    super(AllArgsConstructor.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result;

    result = super.validate(psiAnnotation, psiClass, builder);

    final Collection<PsiField> allNotInitializedNotStaticFields = getAllFields(psiClass);
    final String staticConstructorName = getStaticConstructorName(psiAnnotation);
    if (!validateIsConstructorDefined(psiClass, staticConstructorName, allNotInitializedNotStaticFields, builder)) {
      result = false;
    }
    return result;
  }

  @NotNull
  public Collection<PsiMethod> createAllArgsConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation) {
    final Collection<PsiField> allNotInitializedNotStaticFields = getAllNotInitializedAndNotStaticFields(psiClass);
    return createConstructorMethod(psiClass, methodVisibility, psiAnnotation, allNotInitializedNotStaticFields);
  }

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
  public Collection<PsiField> getAllFields(@NotNull PsiClass psiClass) {
    return getAllNotInitializedAndNotStaticFields(psiClass);
  }

  @NotNull
  public Collection<PsiMethod> createAllArgsConstructor(PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, PsiAnnotation psiAnnotation, String staticName, Collection<PsiField> allNotInitializedNotStaticFields) {
    return createConstructorMethod(psiClass, methodModifier, psiAnnotation, allNotInitializedNotStaticFields, staticName);
  }
}
