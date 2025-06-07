package de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants;


import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemProcessingSink;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.field.FieldNameConstantsFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @FieldNameConstants lombok annotation on a class
 * Creates string constants containing the field name for each field of this class
 * Used for lombok v1.16.22 to lombok v1.18.2 only!
 *
 * @author Plushnikov Michail
 */
public final class FieldNameConstantsOldProcessor extends AbstractClassProcessor {

  public FieldNameConstantsOldProcessor() {
    super(PsiField.class, LombokClassNames.FIELD_NAME_CONSTANTS);
  }

  private static FieldNameConstantsFieldProcessor getFieldNameConstantsFieldProcessor() {
    return LombokProcessorManager.getInstance().getFieldNameConstantsFieldProcessor();
  }

  @Override
  protected boolean supportAnnotationVariant(@NotNull PsiAnnotation psiAnnotation) {
    String prefix = "prefix";
    //it can help for dumb mode or incomplete mode
    if (null != psiAnnotation.findDeclaredAttributeValue(prefix)) return true;
    // old version of @FieldNameConstants has attributes "prefix" and "suffix", the new one not
    return null != psiAnnotation.findAttributeValue(prefix);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    final boolean result = validateAnnotationOnRightType(psiClass, builder) && LombokProcessorUtil.isLevelVisible(psiAnnotation);
    if (result) {
      final Collection<PsiField> psiFields = filterFields(psiClass);
      for (PsiField psiField : psiFields) {
        FieldNameConstantsFieldProcessor.checkIfFieldNameIsValidAndWarn(psiAnnotation, psiField, builder);
      }
    }
    return result;
  }

  private static boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addErrorMessage("inspection.message.field.name.constants.only.supported.on.class.enum.or.field.type");
      result = false;
    }
    return result;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target,
                                     @Nullable String nameHint) {
    final Collection<PsiField> psiFields = filterFields(psiClass);
    for (PsiField psiField : psiFields) {
      if (FieldNameConstantsFieldProcessor.checkIfFieldNameIsValidAndWarn(psiAnnotation, psiField, new ProblemProcessingSink())) {
        target.add(FieldNameConstantsFieldProcessor.createFieldNameConstant(psiField, psiClass, psiAnnotation));
      }
    }
  }

  private static @NotNull Collection<PsiField> filterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiField> psiFields = new ArrayList<>();

    final FieldNameConstantsFieldProcessor fieldProcessor = getFieldNameConstantsFieldProcessor();
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      if (shouldUseField(psiField, fieldProcessor)) {
        psiFields.add(psiField);
      }
    }
    return psiFields;
  }

  private static boolean shouldUseField(@NotNull PsiField psiField, @NotNull FieldNameConstantsFieldProcessor fieldProcessor) {
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
    return useField;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    if (shouldUseField(psiField, getFieldNameConstantsFieldProcessor())) {
      return LombokPsiElementUsage.USAGE;
    }
    return LombokPsiElementUsage.NONE;
  }
}
