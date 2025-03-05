package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
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

/**
 * Inspect and validate @Setter lombok annotation on a class
 * Creates setter methods for fields of this class
 *
 * @author Plushnikov Michail
 */
public final class SetterProcessor extends AbstractClassProcessor {
  public SetterProcessor() {
    super(PsiMethod.class, LombokClassNames.SETTER);
  }

  private static SetterFieldProcessor getSetterFieldProcessor() {
    return LombokProcessorManager.getInstance().getSetterFieldProcessor();
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@NotNull String nameHint,
                                                   @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
      if (nameHint.equals(LombokUtils.getSetterName(psiField, accessorsInfo))) return true;
    }
    return false;
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    Collection<String> result = new ArrayList<>();

    final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
      result.add(LombokUtils.getSetterName(psiField, accessorsInfo));
    }

    return result;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    validateAnnotationOnRightType(psiAnnotation, psiClass, builder);
    validateVisibility(psiAnnotation, builder);
    return builder.success();
  }

  private static void validateAnnotationOnRightType(@NotNull PsiAnnotation psiAnnotation,
                                                    @NotNull PsiClass psiClass,
                                                    @NotNull ProblemSink builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum() || psiClass.isRecord()) {
      builder.addErrorMessage("inspection.message.setter.only.supported.on.class.or.field.type");
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
                                     @NotNull List<? super PsiElement> target,
                                     @Nullable String nameHint) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.addAll(createFieldSetters(psiClass, methodVisibility, nameHint));
    }
  }

  public Collection<PsiMethod> createFieldSetters(@NotNull PsiClass psiClass, @NotNull String methodModifier, @Nullable String nameHint) {
    Collection<PsiMethod> result = new ArrayList<>();

    final Collection<PsiField> setterFields = filterSetterFields(psiClass);

    for (PsiField setterField : setterFields) {
      ContainerUtil.addIfNotNull(result, SetterFieldProcessor.createSetterMethod(setterField, psiClass, methodModifier, nameHint));
    }
    return result;
  }

  private @NotNull Collection<PsiField> filterSetterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> classMethods = filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(psiClass));

    final SetterFieldProcessor fieldProcessor = getSetterFieldProcessor();
    final Collection<PsiField> setterFields = new ArrayList<>();
    for (PsiField psiField : psiClass.getFields()) {
      if (shouldGenerateSetter(psiField, fieldProcessor, classMethods)) {
        setterFields.add(psiField);
      }
    }
    return setterFields;
  }

  private static boolean shouldGenerateSetter(@NotNull PsiField psiField, @NotNull SetterFieldProcessor fieldProcessor,
                                              @NotNull Collection<PsiMethod> classMethods) {
    boolean createSetter = true;
    PsiModifierList modifierList = psiField.getModifierList();
    if (null != modifierList) {
      //Skip final fields.
      createSetter = !modifierList.hasModifierProperty(PsiModifier.FINAL);
      //Skip static fields.
      createSetter &= !modifierList.hasModifierProperty(PsiModifier.STATIC);
      //Skip fields having Setter annotation already
      createSetter &= PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, fieldProcessor.getSupportedAnnotationClasses());
      //Skip fields that start with $
      createSetter &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);

      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
      createSetter &= accessorsInfo.acceptsFieldName(psiField.getName());
      //Skip fields if a method with same name already exists
      final Collection<String> methodNames = LombokUtils.toAllSetterNames(accessorsInfo, psiField.getName(), PsiTypes.booleanType().equals(psiField.getType()));
      for (String methodName : methodNames) {
        createSetter &= !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 1);
      }
    }
    return createSetter;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final Collection<PsiMethod> classMethods = filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(containingClass));

      final SetterFieldProcessor fieldProcessor = getSetterFieldProcessor();

      if (shouldGenerateSetter(psiField, fieldProcessor, classMethods)) {
        return LombokPsiElementUsage.WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
