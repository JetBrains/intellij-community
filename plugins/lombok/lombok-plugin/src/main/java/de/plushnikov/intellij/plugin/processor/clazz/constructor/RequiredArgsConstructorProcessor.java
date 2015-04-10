package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class RequiredArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  public RequiredArgsConstructorProcessor() {
    super(RequiredArgsConstructor.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result;

    result = super.validate(psiAnnotation, psiClass, builder);

    final Collection<PsiField> allReqFields = getRequiredFields(psiClass);
    final String staticConstructorName = getStaticConstructorName(psiAnnotation);
    if (!validateIsConstructorDefined(psiClass, staticConstructorName, allReqFields, builder)) {
      result = false;
    }
    return result;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    if (null != methodVisibility) {
      final Collection<PsiField> allReqFields = getRequiredFields(psiClass);
      target.addAll(createConstructorMethod(psiClass, methodVisibility, psiAnnotation, allReqFields));
    }
  }

  @NotNull
  public Collection<PsiMethod> createRequiredArgsConstructor(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, @Nullable String staticName) {
    final Collection<PsiField> allReqFields = getRequiredFields(psiClass);

    return createConstructorMethod(psiClass, methodModifier, psiAnnotation, allReqFields, staticName);
  }

  @NotNull
  @SuppressWarnings("deprecation")
  public Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> result = new ArrayList<PsiField>();
    final boolean classAnnotatedWithValue = PsiAnnotationUtil.isAnnotatedWith(psiClass, Value.class, lombok.experimental.Value.class);

    for (PsiField psiField : getAllNotInitializedAndNotStaticFields(psiClass)) {
      final PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        final boolean isFinal = isFieldFinal(psiField, modifierList, classAnnotatedWithValue);
        final boolean isNonNull = PsiAnnotationUtil.isAnnotatedWith(psiField, LombokUtils.NON_NULL_PATTERN);
        // accept initialized final or nonnull fields
        if ((isFinal || isNonNull) && null == psiField.getInitializer()) {
          result.add(psiField);
        }
      }
    }
    return result;
  }

}
