package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.*;
import org.jetbrains.annotations.NotNull;

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
    return ApplicationManager.getApplication().getService(GetterFieldProcessor.class);
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
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
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
                                     @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.addAll(createFieldGetters(psiClass, methodVisibility));
    }
  }

  @NotNull
  public Collection<PsiMethod> createFieldGetters(@NotNull PsiClass psiClass, @NotNull String methodModifier) {
    Collection<PsiMethod> result = new ArrayList<>();
    final Collection<PsiField> getterFields = filterGetterFields(psiClass);
    GetterFieldProcessor fieldProcessor = getGetterFieldProcessor();
    for (PsiField getterField : getterFields) {
      result.add(fieldProcessor.createGetterMethod(getterField, psiClass, methodModifier));
    }
    return result;
  }

  @NotNull
  private Collection<PsiField> filterGetterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiField> getterFields = new ArrayList<>();

    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    filterToleratedElements(classMethods);

    final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
    GetterFieldProcessor fieldProcessor = getGetterFieldProcessor();
    for (PsiField psiField : psiClass.getFields()) {
      boolean createGetter = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createGetter = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields having Getter annotation already
        createGetter &= PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, fieldProcessor.getSupportedAnnotationClasses());
        //Skip fields that start with $
        createGetter &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        //Skip fields if a method with same name and arguments count already exists
        final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
        final Collection<String> methodNames =
          LombokUtils.toAllGetterNames(accessorsInfo, psiField.getName(), PsiType.BOOLEAN.equals(psiField.getType()));
        for (String methodName : methodNames) {
          createGetter &= !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0);
        }
      }

      if (createGetter) {
        getterFields.add(psiField);
      }
    }
    return getterFields;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(filterGetterFields(containingClass)).contains(psiField.getName())) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
