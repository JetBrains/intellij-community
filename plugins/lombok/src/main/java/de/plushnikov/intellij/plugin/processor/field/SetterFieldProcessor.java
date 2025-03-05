package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a field
 * Creates setter method for this field
 *
 * @author Plushnikov Michail
 */
public final class SetterFieldProcessor extends AbstractFieldProcessor {
  public SetterFieldProcessor() {
    super(PsiMethod.class, LombokClassNames.SETTER);
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass,
                                                                   @NotNull PsiAnnotation psiAnnotation,
                                                                   @NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    final String generatedElementName = LombokUtils.getSetterName(psiField, accessorsInfo);
    return Collections.singletonList(generatedElementName);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target,
                                     @Nullable String nameHint) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    final PsiClass psiClass = psiField.getContainingClass();
    if (methodVisibility != null && psiClass != null) {
      ContainerUtil.addIfNotNull(target, createSetterMethod(psiField, psiClass, methodVisibility, nameHint));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemSink builder) {
    boolean result;
    validateOnXAnnotations(psiAnnotation, psiField, builder, "onParam");

    result = validateFinalModifier(psiAnnotation, psiField, builder);
    if (result) {
      result = validateVisibility(psiAnnotation);
      if (result) {
        result = validateExistingMethods(psiField, builder, false);
        if (result) {
          result = validateAccessorPrefix(psiField, builder);
        }
      }
    }
    return result;
  }

  private static boolean validateFinalModifier(@NotNull PsiAnnotation psiAnnotation,
                                               @NotNull PsiField psiField,
                                               @NotNull ProblemSink builder) {
    boolean result = true;
    if (psiField.hasModifierProperty(PsiModifier.FINAL) && null != LombokProcessorUtil.getMethodModifier(psiAnnotation)) {
      builder.addWarningMessage("inspection.message.not.generating.setter.for.this.field.setters")
        .withLocalQuickFixes(() -> PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      result = false;
    }
    return result;
  }

  private static boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  private static boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemSink builder) {
    boolean result = true;
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    if (!accessorsInfo.acceptsFieldName(psiField.getName())) {
      builder.addWarningMessage("inspection.message.not.generating.setter.for.this.field.it");
      result = false;
    }
    return result;
  }

  public Collection<String> getAllSetterNames(@NotNull PsiField psiField, boolean isBoolean) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    return LombokUtils.toAllSetterNames(accessorsInfo, psiField.getName(), isBoolean);
  }

  @Contract("_,_,_,null -> !null")
  public static @Nullable PsiMethod createSetterMethod(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull String methodModifier,
                                                       @Nullable String nameHint) {
    final String fieldName = psiField.getName();
    final PsiType psiFieldType = psiField.getType();
    final PsiAnnotation setterAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.SETTER);

    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    final String methodName = LombokUtils.getSetterName(psiField, accessorsInfo);

    if (nameHint != null && !nameHint.equals(methodName)) return null;

    PsiType returnType = getReturnType(psiField, accessorsInfo.isChain());
    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiField.getManager(), methodName)
      .withMethodReturnType(returnType)
      .withContainingClass(psiClass)
      .withParameter(fieldName, psiFieldType)
      .withNavigationElement(psiField)
      .withMutatesThisContract()
      .withWriteAccess();
    if (StringUtil.isNotEmpty(methodModifier)) {
      methodBuilder.withModifier(methodModifier);
    }
    boolean isStatic = psiField.hasModifierProperty(PsiModifier.STATIC);
    if (isStatic) {
      methodBuilder.withModifier(PsiModifier.STATIC);
    }
    if (accessorsInfo.isMakeFinal()) {
      methodBuilder.withModifier(PsiModifier.FINAL);
    }

    LombokLightParameter setterParameter = methodBuilder.getParameterList().getParameter(0);
    if (null != setterParameter) {
      LombokLightModifierList methodParameterModifierList = setterParameter.getModifierList();
      LombokCopyableAnnotations.copyCopyableAnnotations(psiField, methodParameterModifierList, LombokCopyableAnnotations.BASE_COPYABLE);
      LombokCopyableAnnotations.copyOnXAnnotations(setterAnnotation, methodParameterModifierList, "onParam");
    }

    final LombokLightModifierList modifierList = methodBuilder.getModifierList();
    LombokCopyableAnnotations.copyCopyableAnnotations(psiField, modifierList, LombokCopyableAnnotations.COPY_TO_SETTER);
    LombokCopyableAnnotations.copyOnXAnnotations(setterAnnotation, modifierList, "onMethod");
    if (psiField.isDeprecated()) {
      modifierList.addAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED);
    }

    final String codeBlockText = createCodeBlockText(psiField, psiClass, returnType, isStatic, setterParameter);
    methodBuilder.withBodyText(codeBlockText);

    if (!PsiTypes.voidType().equals(returnType)) {
      LombokAddNullAnnotations.createRelevantNonNullAnnotation(psiClass, methodBuilder);
    }

    return methodBuilder;
  }

  private static @NotNull String createCodeBlockText(@NotNull PsiField psiField,
                                                     @NotNull PsiClass psiClass,
                                                     PsiType returnType,
                                                     boolean isStatic,
                                                     PsiParameter methodParameter) {
    final String blockText;
    final String thisOrClass = isStatic ? psiClass.getName() : "this";
    blockText = String.format("%s.%s = %s; ", thisOrClass, psiField.getName(), methodParameter.getName());

    String codeBlockText = blockText;
    if (!isStatic && !PsiTypes.voidType().equals(returnType)) {
      codeBlockText += "return this;";
    }

    return codeBlockText;
  }

  private static PsiType getReturnType(@NotNull PsiField psiField, boolean isChained) {
    PsiType result = PsiTypes.voidType();
    if (!psiField.hasModifierProperty(PsiModifier.STATIC) && isChained) {
      final PsiClass fieldClass = psiField.getContainingClass();
      if (null != fieldClass) {
        result = PsiClassUtil.getTypeWithGenerics(fieldClass);
      }
    }
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.WRITE;
  }
}
