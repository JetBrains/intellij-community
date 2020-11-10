package de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractFieldNameConstantsProcessor extends AbstractClassProcessor {

  private static final String FIELD_NAME_CONSTANTS_INCLUDE = LombokClassNames.FIELD_NAME_CONSTANTS_INCLUDE;
  private static final String FIELD_NAME_CONSTANTS_EXCLUDE = LombokClassNames.FIELD_NAME_CONSTANTS_EXCLUDE;

  AbstractFieldNameConstantsProcessor(@NotNull Class<? extends PsiElement> supportedClass, @NotNull String supportedAnnotationClass) {
    super(supportedClass, supportedAnnotationClass);
  }

  @Override
  protected boolean supportAnnotationVariant(@NotNull PsiAnnotation psiAnnotation) {
    // new version of @FieldNameConstants has an attribute "asEnum", the old one not
    return null != psiAnnotation.findAttributeValue("asEnum");
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiClass, builder) && LombokProcessorUtil.isLevelVisible(psiAnnotation);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError(LombokBundle.message("inspection.message.field.name.constants.only.supported.on.class.or.enum"));
      return false;
    }
    return true;
  }

  @NotNull
  Collection<PsiField> filterFields(@NotNull PsiClass psiClass, PsiAnnotation psiAnnotation) {
    final Collection<PsiField> psiFields = new ArrayList<>();

    final boolean onlyExplicitlyIncluded = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "onlyExplicitlyIncluded", false);

    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      boolean useField = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {

        //Skip static fields.
        useField = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip transient fields
        useField &= !modifierList.hasModifierProperty(PsiModifier.TRANSIENT);
      }
      //Skip fields that start with $
      useField &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
      //Skip fields annotated with @FieldNameConstants.Exclude
      useField &= !PsiAnnotationSearchUtil.isAnnotatedWith(psiField, FIELD_NAME_CONSTANTS_EXCLUDE);

      if (onlyExplicitlyIncluded) {
        //Only use fields annotated with @FieldNameConstants.Include, Include annotation overrides other rules
        useField = PsiAnnotationSearchUtil.isAnnotatedWith(psiField, FIELD_NAME_CONSTANTS_INCLUDE);
      }

      if (useField) {
        psiFields.add(psiField);
      }
    }
    return psiFields;
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, FIELD_NAME_CONSTANTS_INCLUDE, FIELD_NAME_CONSTANTS_EXCLUDE);
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(filterFields(containingClass, psiAnnotation)).contains(psiField.getName())) {
        return LombokPsiElementUsage.USAGE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
