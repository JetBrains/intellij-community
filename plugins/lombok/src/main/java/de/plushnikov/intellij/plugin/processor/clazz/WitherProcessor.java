package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class WitherProcessor extends AbstractClassProcessor {
  private static final String BUILDER_DEFAULT_ANNOTATION = LombokClassNames.BUILDER_DEFAULT;

  public WitherProcessor() {
    super(PsiMethod.class, LombokClassNames.WITHER, LombokClassNames.WITH);
  }

  private static WitherFieldProcessor getWitherFieldProcessor() {
    return LombokProcessorManager.getInstance().getWitherFieldProcessor();
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@NotNull String nameHint,
                                                   @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    if (!nameHint.startsWith("with")) return false;
    final Collection<? extends PsiVariable> possibleWithElements = getPossibleWithElements(psiClass);
    if (possibleWithElements.isEmpty()) return false;
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiClass).withFluent(false);
    for (PsiVariable possibleWithElement : possibleWithElements) {
      if (nameHint.equals(LombokUtils.getWitherName(possibleWithElement, accessorsInfo))) return true;
    }
    return false;
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    Collection<String> result = new ArrayList<>();

    final Collection<? extends PsiVariable> possibleWithElements = getPossibleWithElements(psiClass);
    if (!possibleWithElements.isEmpty()) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiClass).withFluent(false);
      for (PsiVariable possibleWithElement : possibleWithElements) {
        result.add(LombokUtils.getWitherName(possibleWithElement, accessorsInfo));
      }
    }

    return result;
  }

  private static @NotNull Collection<? extends PsiVariable> getPossibleWithElements(@NotNull PsiClass psiClass) {
    if (psiClass.isRecord()) {
      return List.of(psiClass.getRecordComponents());
    }
    else {
      return PsiClassUtil.collectClassFieldsIntern(psiClass);
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    validateAnnotationOnRightType(psiClass, builder);
    validateVisibility(psiAnnotation, builder);
    if (builder.success()) {
      WitherFieldProcessor.validConstructor(psiClass, builder);
    }
    return builder.success();
  }

  private static void validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addErrorMessage("inspection.message.wither.only.supported.on.class.or.field");
      builder.markFailed();
    }
  }

  private static void validateVisibility(@NotNull PsiAnnotation psiAnnotation, @NotNull ProblemSink builder) {
    if (null == LombokProcessorUtil.getMethodModifier(psiAnnotation)) {
      builder.markFailed();
    }
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target, @Nullable String nameHint) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiClass).withFluent(false);
      target.addAll(createFieldWithers(psiClass, methodVisibility, accessorsInfo));
    }
  }

  private @NotNull Collection<PsiMethod> createFieldWithers(@NotNull PsiClass psiClass,
                                                            @NotNull String methodModifier,
                                                            @NotNull AccessorsInfo accessors) {
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

  private @NotNull Collection<PsiField> getWitherFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> witherFields = new ArrayList<>();

    final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
    final Collection<PsiMethod> existingMethods = filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(psiClass));
    for (PsiField psiField : psiClass.getFields()) {
      if (shouldGenerateWither(psiField, classAccessorsValues, existingMethods)) {
        witherFields.add(psiField);
      }
    }
    return witherFields;
  }

  private static boolean shouldGenerateWither(@NotNull PsiField psiField, @NotNull AccessorsInfo.AccessorsValues classAccessorsValues,
                                              @NotNull Collection<PsiMethod> existingMethods) {
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

      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues).withFluent(false);
      createWither &= accessorsInfo.acceptsFieldName(psiField.getName());

      if (createWither) {
        createWither = isWitherMethodUnique(psiField, accessorsInfo, existingMethods);
      }
    }
    return createWither;
  }

  private static boolean isWitherMethodUnique(@NotNull PsiField psiField, @NotNull AccessorsInfo accessorsInfo,
                                              @NotNull Collection<PsiMethod> existingMethods) {
    final Collection<String> possibleWitherNames =
      LombokUtils.toAllWitherNames(accessorsInfo, psiField.getName(), PsiTypes.booleanType().equals(psiField.getType()));
    for (String witherName : possibleWitherNames) {
      if (PsiMethodUtil.hasSimilarMethod(existingMethods, witherName, 1)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (containingClass != null) {
      final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(containingClass);
      final Collection<PsiMethod> existingMethods = filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(containingClass));

      if (shouldGenerateWither(psiField, classAccessorsValues, existingMethods)) {
        return LombokPsiElementUsage.READ_WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
