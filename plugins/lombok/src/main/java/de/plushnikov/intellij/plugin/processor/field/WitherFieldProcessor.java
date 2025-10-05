package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AbstractConstructorClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokAddNullAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class WitherFieldProcessor extends AbstractFieldProcessor {
  public WitherFieldProcessor() {
    super(PsiMethod.class, LombokClassNames.WITHER, LombokClassNames.WITH);
  }

  private static RequiredArgsConstructorProcessor getRequiredArgsConstructorProcessor() {
    return LombokProcessorManager.getInstance().getRequiredArgsConstructorProcessor();
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass,
                                                                   @NotNull PsiAnnotation psiAnnotation,
                                                                   @NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    final String generatedElementName = LombokUtils.getWitherName(psiField, accessorsInfo);
    return Collections.singletonList(generatedElementName);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemSink builder) {
    validateOnXAnnotations(psiAnnotation, psiField, builder, "onParam");

    final PsiClass containingClass = psiField.getContainingClass();

    boolean valid = null != containingClass;
    valid &= validateVisibility(psiAnnotation);
    valid &= validName(psiField, builder);
    valid &= validNonStatic(psiField, builder);
    valid &= validNonFinalInitialized(psiField, builder);

    if (valid) {
      valid = validIsWitherUnique(psiField, builder,
                                  filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(containingClass)));

      valid &= containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || validConstructor(containingClass, builder);
    }

    return valid;
  }

  private static boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target, @Nullable String nameHint) {
    String methodModifier = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodModifier != null) {
      AccessorsInfo accessorsInfo = buildAccessorsInfo(psiField);
      PsiMethod method = createWitherMethod(psiField, methodModifier, accessorsInfo);
      if (method != null) {
        target.add(method);
      }
    }
  }

  private static boolean validName(@NotNull PsiField psiField, @NotNull ProblemSink builder) {
    if (psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER)) {
      builder.addWarningMessage("inspection.message.not.generating.wither.for.this.field.withers");
      return false;
    }
    return true;
  }

  private static boolean validNonStatic(@NotNull PsiField psiField, final @NotNull ProblemSink builder) {
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      builder.addWarningMessage("inspection.message.not.generating.wither")
        .withLocalQuickFixes(() -> PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.STATIC, false, false));
      return false;
    }
    return true;
  }

  private static boolean validNonFinalInitialized(@NotNull PsiField psiField, @NotNull ProblemSink builder) {
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass &&
        psiField.hasModifierProperty(PsiModifier.FINAL) && !PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.VALUE) &&
        psiField.hasInitializer() && !PsiAnnotationSearchUtil.isAnnotatedWith(psiField, LombokClassNames.BUILDER_DEFAULT)) {
      builder.addWarningMessage("inspection.message.not.generating.wither.for.this.field.withers.cannot.be.generated")
        .withLocalQuickFixes(() -> PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      return false;
    }
    return true;
  }

  private static boolean validIsWitherUnique(@NotNull PsiField psiField, @NotNull ProblemSink builder,
                                             @NotNull Collection<PsiMethod> existingMethods) {
    final AccessorsInfo accessorsInfo = buildAccessorsInfo(psiField);
    final Collection<String> possibleWitherNames =
      LombokUtils.toAllWitherNames(accessorsInfo, psiField.getName(), PsiTypes.booleanType().equals(psiField.getType()));
    for (String witherName : possibleWitherNames) {
      if (PsiMethodUtil.hasSimilarMethod(existingMethods, witherName, 1)) {
        builder.addWarningMessage("inspection.message.not.generating.s.method.with.that.name.already.exists", witherName);
        return false;
      }
    }
    return true;
  }

  public static boolean validConstructor(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass.isRecord() ||
        PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.ALL_ARGS_CONSTRUCTOR,
                                                LombokClassNames.VALUE,
                                                LombokClassNames.BUILDER)) {
      return true;
    }

    final Collection<PsiField> constructorParameters = filterFields(psiClass);

    if (PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.DATA)) {
      final Collection<PsiField> requiredConstructorParameters = AbstractConstructorClassProcessor.getRequiredFields(psiClass);
      if (constructorParameters.size() == requiredConstructorParameters.size()) {
        return true;
      }
    }

    final Collection<PsiMethod> classConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);

    boolean constructorExists = false;
    for (PsiMethod classConstructor : classConstructors) {
      if (classConstructor.getParameterList().getParametersCount() == constructorParameters.size()) {
        constructorExists = true;
        break;
      }
    }

    if (!constructorExists) {
      builder.addWarningMessage("inspection.message.wither.needs.constructor.for.all.fields.d.parameters", constructorParameters.size());
      builder.markFailed();
    }
    return constructorExists;
  }

  private static Collection<PsiField> filterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);

    Collection<PsiField> result = new ArrayList<>(psiFields.size());
    for (PsiField classField : psiFields) {
      final String classFieldName = classField.getName();
      if (classFieldName.startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER)) {
        continue;
      }
      if (classField.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      if (classField.hasModifierProperty(PsiModifier.FINAL) && classField.hasInitializer()) {
        continue;
      }

      result.add(classField);
    }
    return result;
  }

  public @Nullable PsiMethod createWitherMethod(@NotNull PsiField psiField,
                                                @NotNull String methodModifier,
                                                @NotNull AccessorsInfo accessorsInfo) {
    LombokLightMethodBuilder methodBuilder = null;
    final PsiClass psiFieldContainingClass = psiField.getContainingClass();
    if (psiFieldContainingClass != null) {
      final String witherName = LombokUtils.getWitherName(psiField, accessorsInfo);
      final PsiType returnType = PsiClassUtil.getTypeWithGenerics(psiFieldContainingClass);

      methodBuilder = new LombokLightMethodBuilder(psiField.getManager(), witherName)
        .withMethodReturnType(returnType)
        .withContainingClass(psiFieldContainingClass)
        .withNavigationElement(psiField)
        .withModifier(methodModifier)
        .withPureContract()
        .withWriteAccess();

      if (accessorsInfo.isMakeFinal()) {
        methodBuilder.withModifier(PsiModifier.FINAL);
      }

      PsiAnnotation witherAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.WITHER, LombokClassNames.WITH);
      LombokCopyableAnnotations.copyOnXAnnotations(witherAnnotation, methodBuilder.getModifierList(), "onMethod");

      final String psiFieldName = psiField.getName();
      final PsiType psiFieldType = psiField.getType();
      final LombokLightParameter methodParameter = new LombokLightParameter(psiFieldName, psiFieldType, methodBuilder);
      methodBuilder.withParameter(methodParameter);

      LombokLightModifierList methodParameterModifierList = methodParameter.getModifierList();
      LombokCopyableAnnotations.copyCopyableAnnotations(psiField, methodParameterModifierList, LombokCopyableAnnotations.BASE_COPYABLE);
      LombokCopyableAnnotations.copyOnXAnnotations(witherAnnotation, methodParameterModifierList, "onParam");

      if (psiFieldContainingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        methodBuilder.withModifier(PsiModifier.ABSTRACT);
      }
      else {
        final String paramString = getConstructorCall(psiField, psiFieldContainingClass);
        final String blockText =
          String.format("return this.%s == %s ? this : new %s(%s);", psiFieldName, psiFieldName, returnType.getCanonicalText(),
                        paramString);
        methodBuilder.withBodyText(blockText);
      }

      LombokAddNullAnnotations.createRelevantNonNullAnnotation(psiFieldContainingClass, methodBuilder);
    }
    return methodBuilder;
  }

  private static AccessorsInfo buildAccessorsInfo(@NotNull PsiField psiField) {
    return AccessorsInfo.buildFor(psiField).withFluent(false);
  }

  private static String getConstructorCall(@NotNull PsiField psiField, @NotNull PsiClass psiClass) {
    final StringBuilder paramString = new StringBuilder();
    final Collection<PsiField> psiFields = filterFields(psiClass);
    for (PsiField classField : psiFields) {
      final String classFieldName = classField.getName();
      if (classField.equals(psiField)) {
        paramString.append(classFieldName);
      }
      else {
        paramString.append("this.").append(classFieldName);
      }
      paramString.append(',');
    }
    if (paramString.length() > 1) {
      paramString.deleteCharAt(paramString.length() - 1);
    }
    return paramString.toString();
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}
