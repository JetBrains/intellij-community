package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.FieldNameConstantsFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.FieldNameConstants;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @FieldNameConstants lombok annotation on a class
 * Creates string constants containing the field name for each field of this class
 *
 * @author Plushnikov Michail
 */
public class FieldNameConstantsProcessor extends AbstractClassProcessor {

  private final FieldNameConstantsFieldProcessor fieldProcessor;

  public FieldNameConstantsProcessor(FieldNameConstantsFieldProcessor fieldProcessor) {
    super(PsiField.class, FieldNameConstants.class);
    this.fieldProcessor = fieldProcessor;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRightType(psiClass, builder) && LombokProcessorUtil.isLevelVisible(psiAnnotation);
    if (result) {
      final Collection<PsiField> psiFields = filterFields(psiClass);
      for (PsiField psiField : psiFields) {
        fieldProcessor.checkIfFieldNameIsValidAndWarn(psiAnnotation, psiField, builder);
      }
    }
    return result;
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("'@FieldNameConstants' is only supported on a class, enum or field type");
      result = false;
    }
    return result;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final Collection<PsiField> psiFields = filterFields(psiClass);
    for (PsiField psiField : psiFields) {
      if (fieldProcessor.checkIfFieldNameIsValidAndWarn(psiAnnotation, psiField, ProblemEmptyBuilder.getInstance())) {
        target.add(fieldProcessor.createFieldNameConstant(psiField, psiClass, psiAnnotation));
      }
    }
  }

  @NotNull
  private Collection<PsiField> filterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiField> psiFields = new ArrayList<>();

    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      boolean useField = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        useField = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields having same annotation already
        useField &= PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, fieldProcessor.getSupportedAnnotationClasses());
        //Skip fields that start with $
        useField &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
      }

      if (useField) {
        psiFields.add(psiField);
      }
    }
    return psiFields;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(filterFields(containingClass)).contains(psiField.getName())) {
        return LombokPsiElementUsage.USAGE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
