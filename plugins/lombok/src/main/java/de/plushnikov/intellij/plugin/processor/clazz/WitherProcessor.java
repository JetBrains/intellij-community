package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WitherProcessor extends AbstractClassProcessor {
  private static final String BUILDER_DEFAULT_ANNOTATION = LombokClassNames.BUILDER_DEFAULT;

  public WitherProcessor() {
    super(PsiMethod.class, LombokClassNames.WITHER, LombokClassNames.WITH);
  }

  private WitherFieldProcessor getWitherFieldProcessor() {
    return ApplicationManager.getApplication().getService(WitherFieldProcessor.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiClass, builder) && validateVisibility(psiAnnotation) &&
      getWitherFieldProcessor().validConstructor(psiClass, builder);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(LombokBundle.message("inspection.message.wither.only.supported.on.class.or.field"));
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
    Collection<PsiMethod> result = new ArrayList<>();

    final Collection<PsiField> witherFields = getWitherFields(psiClass);

    for (PsiField witherField : witherFields) {
      PsiMethod method = getWitherFieldProcessor().createWitherMethod(witherField, methodModifier, accessors);
      if (method != null) {
        result.add(method);
      }
    }

    return result;
  }

  @NotNull
  private Collection<PsiField> getWitherFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> witherFields = new ArrayList<>();
    for (PsiField psiField : psiClass.getFields()) {
      boolean createWither = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        // Skip static fields.
        createWither = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        // Skip final fields that are initialized and not annotated with @Builder.Default
        createWither &= !(modifierList.hasModifierProperty(PsiModifier.FINAL) && psiField.hasInitializer() &&
          PsiAnnotationSearchUtil.findAnnotation(psiField, BUILDER_DEFAULT_ANNOTATION) == null);
        // Skip fields that start with $
        createWither &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // Skip fields having Wither annotation already
        createWither &= !PsiAnnotationSearchUtil.isAnnotatedWith(psiField, LombokClassNames.WITHER, LombokClassNames.WITH);
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
