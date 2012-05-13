package de.plushnikov.intellij.lombok.processor.clazz.constructor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.lombok.LombokUtils;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.util.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import lombok.RequiredArgsConstructor;
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

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    if (null != methodVisibility) {
      final Collection<PsiField> allReqFields = getRequiredFields(psiClass);
      target.addAll((Collection<? extends Psi>) createConstructorMethod(psiClass, methodVisibility, psiAnnotation, allReqFields));
    }
  }

  @NotNull
  public Collection<PsiMethod> createRequiredArgsConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation, @Nullable String staticName) {
    final Collection<PsiField> allReqFields = getRequiredFields(psiClass);

    return createConstructorMethod(psiClass, methodVisibility, psiAnnotation, allReqFields, staticName);
  }

  @NotNull
  public Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> result = new ArrayList<PsiField>();
    for (PsiField psiField : getAllNotInitializedAndNotStaticFields(psiClass)) {
      boolean addField = false;

      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        final boolean isFinal = modifierList.hasModifierProperty(PsiModifier.FINAL);
        final boolean isNonNull = PsiAnnotationUtil.isAnnotatedWith(psiField, LombokUtils.NON_NULL_PATTERN);
        // accept initialized final or nonnull fields
        addField = (isFinal || isNonNull) && null == psiField.getInitializer();
      }

      if (addField) {
        result.add(psiField);
      }
    }
    return result;
  }

}
