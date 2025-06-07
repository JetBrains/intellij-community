package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Getter lombok annotation on a class
 * Creates getter methods for fields of this class
 *
 * @author Plushnikov Michail
 */
public final class GetterProcessor extends AbstractClassProcessor {
  public GetterProcessor() {
    super(PsiMethod.class, LombokClassNames.GETTER);
  }

  private static GetterFieldProcessor getGetterFieldProcessor() {
    return LombokProcessorManager.getInstance().getGetterFieldProcessor();
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@NotNull String nameHint,
                                                   @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
      if (nameHint.equals(LombokUtils.getGetterName(psiField, accessorsInfo))) return true;
    }
    return false;
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    Collection<String> result = new ArrayList<>();

    final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
      result.add(LombokUtils.getGetterName(psiField, accessorsInfo));
    }

    return result;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    validateAnnotationOnRightType(psiClass, builder);
    validateVisibility(psiAnnotation, builder);

    if (builder.deepValidation()) {
      if (PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "lazy", false)) {
        builder.addWarningMessage("inspection.message.lazy.not.supported.for.getter.on.type");
      }
    }
    return builder.success();
  }

  private static void validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isRecord()) {
      builder.addErrorMessage("inspection.message.getter.only.supported.on.class.enum.or.field.type");
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
      target.addAll(createFieldGetters(psiClass, methodVisibility, nameHint));
    }
  }

  public @NotNull Collection<PsiMethod> createFieldGetters(@NotNull PsiClass psiClass, @NotNull String methodModifier, @Nullable String nameHint) {
    Collection<PsiMethod> result = new ArrayList<>();

    final Collection<PsiField> getterFields = filterGetterFields(psiClass);
    for (PsiField getterField : getterFields) {
      ContainerUtil.addIfNotNull(result, GetterFieldProcessor.createGetterMethod(getterField, psiClass, methodModifier, nameHint));
    }
    return result;
  }

  private @NotNull Collection<PsiField> filterGetterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiField> getterFields = new ArrayList<>();

    Collection<PsiMethod> classMethods = filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(psiClass));

    final GetterFieldProcessor fieldProcessor = getGetterFieldProcessor();
    final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
    for (PsiField psiField : psiClass.getFields()) {
      if (shouldCreateGetter(psiField, fieldProcessor, classAccessorsValues, classMethods)) {
        getterFields.add(psiField);
      }
    }
    return getterFields;
  }

  private static boolean shouldCreateGetter(@NotNull PsiField psiField,
                                            @NotNull GetterFieldProcessor fieldProcessor,
                                            @NotNull AccessorsInfo.AccessorsValues classAccessorsValues,
                                            @NotNull Collection<PsiMethod> classMethods) {
    boolean createGetter = true;
    PsiModifierList modifierList = psiField.getModifierList();
    if (null != modifierList) {
      //Skip static fields.
      createGetter = !modifierList.hasModifierProperty(PsiModifier.STATIC);
      //Skip fields having Getter annotation already
      createGetter &= PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, fieldProcessor.getSupportedAnnotationClasses());
      //Skip fields that start with $
      createGetter &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);

      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
      createGetter &= accessorsInfo.acceptsFieldName(psiField.getName());
      //Skip fields if a method with same name and arguments count already exists
      final Collection<String> methodNames =
        LombokUtils.toAllGetterNames(accessorsInfo, psiField.getName(), PsiTypes.booleanType().equals(psiField.getType()));
      for (String methodName : methodNames) {
        createGetter &= !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0);
      }
    }
    return createGetter;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final Collection<PsiMethod> classMethods = filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(containingClass));


      final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(containingClass);
      final GetterFieldProcessor fieldProcessor = getGetterFieldProcessor();

      if (shouldCreateGetter(psiField, fieldProcessor, classAccessorsValues, classMethods)) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
