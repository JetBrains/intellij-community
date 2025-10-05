package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.WithByFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @WithBy lombok annotation on a class
 * Creates lombok 'withBy' methods for fields of this class
 *
 * @author Plushnikov Michail
 */
public final class WithByProcessor extends AbstractClassProcessor {
  public WithByProcessor() {
    super(PsiMethod.class, LombokClassNames.WITH_BY);
  }

  private static WithByFieldProcessor getFieldProcessor() {
    return LombokProcessorManager.getInstance().getWithByFieldProcessor();
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    Collection<String> result = new ArrayList<>();

    final Collection<PsiField> psiFields = filterWithByElements(psiClass);
    if (!psiFields.isEmpty()) {
      final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);

      for (PsiField psiField : psiFields) {
        final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
        result.add(LombokUtils.getWithByName(psiField, accessorsInfo));
      }
    }

    return result;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    validateAnnotationOnRightType(psiClass, builder);
    validateVisibility(psiAnnotation, builder);
    validateOnX(psiAnnotation, builder);
    return builder.success();
  }

  private static void validateOnX(@NotNull PsiAnnotation psiAnnotation, @NotNull ProblemSink builder) {
    final Iterable<String> onXAnnotations = LombokProcessorUtil.getOnX(psiAnnotation, "onMethod");
    if (onXAnnotations.iterator().hasNext()) {
      builder.addErrorMessage("inspection.message.withby.onmethod.not.supported.on.class.type");
      builder.markFailed();
    }
  }

  private static void validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addErrorMessage("inspection.message.withby.only.supported.on.class.or.field.type");
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
      target.addAll(createFieldWithBys(psiClass, methodVisibility, nameHint));
    }
  }

  private @NotNull Collection<PsiMethod> createFieldWithBys(@NotNull PsiClass psiClass,
                                                                                                @NotNull String methodModifier,
                                                                                                @Nullable String nameHint) {
    Collection<PsiMethod> result = new ArrayList<>();

    final Collection<PsiField> psiFields = filterWithByElements(psiClass);
    if (!psiFields.isEmpty()) {
      final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);

      for (PsiField psiField : psiFields) {
        final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
        ContainerUtil.addIfNotNull(result, getFieldProcessor().createWithByMethod(psiField, methodModifier, accessorsInfo, nameHint));
      }
    }
    return result;
  }

  private @NotNull Collection<PsiField> filterWithByElements(@NotNull PsiClass psiClass) {
    final Collection<PsiField> psiElements = new ArrayList<>();

    final Collection<PsiField> psiFields;
    if (psiClass.isRecord()) {
      psiFields = Arrays.asList(psiClass.getFields());
    }
    else {
      psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    }
    for (PsiField psiField : psiFields) {
      if (shouldCreateWithBy(psiField)) {
        psiElements.add(psiField);
      }
    }

    return psiElements;
  }

  private static boolean shouldCreateWithBy(@NotNull PsiField psiField) {
    boolean createWithBy;
    //Skip static fields.
    createWithBy = !psiField.hasModifierProperty(PsiModifier.STATIC);
    //Skip fields that start with $
    createWithBy &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
    //Skip fields having this annotation already
    createWithBy &= PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, LombokClassNames.WITH_BY);
    //Skip initialized final fields.
    createWithBy &= !(psiField.hasModifierProperty(PsiModifier.FINAL) && psiField.hasInitializer());
    return createWithBy;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    if (shouldCreateWithBy(psiField)) {
      return LombokPsiElementUsage.READ_WRITE;
    }
    return LombokPsiElementUsage.NONE;
  }
}
