package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.Wither;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WitherProcessor extends AbstractClassProcessor {
  private final WitherFieldProcessor fieldProcessor;

  public WitherProcessor(WitherFieldProcessor fieldProcessor) {
    super(PsiMethod.class, Wither.class);
    this.fieldProcessor = fieldProcessor;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiClass, builder) && validateVisibility(psiAnnotation) && fieldProcessor.validConstructor(psiClass, builder);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("@Wither is only supported on a class or a field.");
      result = false;
    }
    return result;
  }

  private boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiClass).withFluent(false);
      target.addAll(createFieldWithers(psiClass, methodVisibility, accessorsInfo));
    }
  }

  @NotNull
  private Collection<PsiMethod> createFieldWithers(@NotNull PsiClass psiClass, @NotNull String methodModifier, @NotNull AccessorsInfo accessors) {
    Collection<PsiMethod> result = new ArrayList<PsiMethod>();

    final Collection<PsiField> witherFields = getWitherFields(psiClass);

    for (PsiField witherField : witherFields) {
      PsiMethod method = fieldProcessor.createWitherMethod(witherField, methodModifier, accessors);
      if (method != null) {
        result.add(method);
      }
    }

    return result;
  }

  @NotNull
  private Collection<PsiField> getWitherFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> witherFields = new ArrayList<PsiField>();
    for (PsiField psiField : psiClass.getFields()) {
      boolean createWither = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        // Skip static fields.
        createWither = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        // Skip final fields
        createWither &= !(modifierList.hasModifierProperty(PsiModifier.FINAL) && psiField.hasInitializer());
        // Skip fields that start with $
        createWither &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // Skip fields having Wither annotation already
        createWither &= !PsiAnnotationSearchUtil.isAnnotatedWith(psiField, Wither.class);
      }
      if (createWither) {
        witherFields.add(psiField);
      }
    }
    return witherFields;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(getWitherFields(containingClass)).contains(psiField.getName())) {
        return LombokPsiElementUsage.READ_WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
