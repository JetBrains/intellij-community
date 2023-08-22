package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

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
    return ApplicationManager.getApplication().getService(SetterFieldProcessor.class);
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
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addErrorMessage("inspection.message.s.only.supported.on.class.or.field.type", psiAnnotation.getQualifiedName());
      builder.markFailed();
    }
  }

  private static void validateVisibility(@NotNull PsiAnnotation psiAnnotation, @NotNull ProblemSink builder) {
    if(null == LombokProcessorUtil.getMethodModifier(psiAnnotation)) {
      builder.markFailed();
    }
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.addAll(createFieldSetters(psiClass, methodVisibility));
    }
  }

  public Collection<PsiMethod> createFieldSetters(@NotNull PsiClass psiClass, @NotNull String methodModifier) {
    Collection<PsiMethod> result = new ArrayList<>();

    final Collection<PsiField> setterFields = filterSetterFields(psiClass);

    for (PsiField setterField : setterFields) {
      result.add(SetterFieldProcessor.createSetterMethod(setterField, psiClass, methodModifier));
    }
    return result;
  }

  @NotNull
  private Collection<PsiField> filterSetterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    filterToleratedElements(classMethods);

    SetterFieldProcessor fieldProcessor = getSetterFieldProcessor();
    final Collection<PsiField> setterFields = new ArrayList<>();
    for (PsiField psiField : psiClass.getFields()) {
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
        //Skip fields if a method with same name already exists
        final Collection<String> methodNames = fieldProcessor.getAllSetterNames(psiField, PsiTypes.booleanType().equals(psiField.getType()));
        for (String methodName : methodNames) {
          createSetter &= !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 1);
        }
      }
      if (createSetter) {
        setterFields.add(psiField);
      }
    }
    return setterFields;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(filterSetterFields(containingClass)).contains(psiField.getName())) {
        return LombokPsiElementUsage.WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
