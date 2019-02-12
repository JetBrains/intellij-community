package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.handler.FieldNameConstantsHandler;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.FieldNameConstants;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @FieldNameConstants lombok annotation on a field
 * Creates Inner class containing string constants of the field name for each field of this class
 *
 * @author Plushnikov Michail
 */
public class FieldNameConstantsProcessor extends AbstractClassProcessor {

  private static final String FIELD_NAME_CONSTANTS_INCLUDE = FieldNameConstants.Include.class.getName().replace("$", ".");
  private static final String FIELD_NAME_CONSTANTS_EXCLUDE = FieldNameConstants.Exclude.class.getName().replace("$", ".");

  public FieldNameConstantsProcessor(@NotNull ConfigDiscovery configDiscovery) {
    super(configDiscovery, PsiClass.class, FieldNameConstants.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiClass, builder) && LombokProcessorUtil.isLevelVisible(psiAnnotation);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("'@FieldNameConstants' is only supported on a class or enum");
      return false;
    }
    return true;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final Collection<PsiField> psiFields = filterFields(psiClass, psiAnnotation);
    if (!psiFields.isEmpty()) {
      PsiClass innerClassOrEnum = FieldNameConstantsHandler.createInnerClassOrEnum(psiClass, psiAnnotation, psiFields);
      if (innerClassOrEnum != null) {
        target.add(innerClassOrEnum);
      }
    }
  }

  @NotNull
  private Collection<PsiField> filterFields(@NotNull PsiClass psiClass, PsiAnnotation psiAnnotation) {
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
