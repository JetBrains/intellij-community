package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.Wither;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WitherProcessor extends AbstractClassProcessor {
  private final WitherFieldProcessor fieldProcessor = new WitherFieldProcessor();

  public WitherProcessor() {
    super(Wither.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiClass, builder) && validateVisibility(psiAnnotation) && fieldProcessor.validConstructor(psiClass, builder);
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("@Wither is only supported on a class or a field.");
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiClass);
      target.addAll(createFieldWithers(psiClass, psiAnnotation, methodVisibility, accessorsInfo));
    }
  }

  @NotNull
  public Collection<PsiMethod> createFieldWithers(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull String methodModifier, @NotNull AccessorsInfo accessors) {
    Collection<PsiMethod> result = new ArrayList<PsiMethod>();

    for (PsiField psiField : psiClass.getFields()) {
      boolean createWither = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        // Skip static fields.
        createWither = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        // Skip final fields
        createWither &= !(modifierList.hasModifierProperty(PsiModifier.FINAL) && psiField.hasInitializer());
        // Skip fields that start with $
        createWither &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // Skip fields having Wither annotation already
        createWither &= !PsiAnnotationUtil.isAnnotatedWith(psiField, Wither.class);
      }
      if (createWither) {
        PsiMethod method = fieldProcessor.createWitherMethod(psiField, methodModifier, accessors);
        if (method != null) {
          result.add(method);
        }
      }
    }
    return result;
  }
}
