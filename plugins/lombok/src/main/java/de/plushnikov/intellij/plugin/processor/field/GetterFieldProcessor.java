package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Inspect and validate @Getter lombok annotation on a field
 * Creates getter method for this field
 *
 * @author Plushnikov Michail
 */
public final class GetterFieldProcessor extends AbstractFieldProcessor {

  public GetterFieldProcessor() {
    super(PsiMethod.class, LombokClassNames.GETTER);
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass,
                                                                   @NotNull PsiAnnotation psiAnnotation,
                                                                   @NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    final String generatedElementName = LombokUtils.getGetterName(psiField, accessorsInfo);
    return Collections.singletonList(generatedElementName);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target,
                                     @Nullable String nameHint) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != methodVisibility && null != psiClass) {
      ContainerUtil.addIfNotNull(target, createGetterMethod(psiField, psiClass, methodVisibility, nameHint));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemSink builder) {
    boolean result;

    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    result = null != methodVisibility;

    final boolean lazy = isLazyGetter(psiAnnotation);
    if (null == methodVisibility && lazy) {
      builder.addWarningMessage("inspection.message.lazy.does.not.work.with.access.level.none");
    }

    if (result && lazy) {
      if (!psiField.hasModifierProperty(PsiModifier.FINAL) || !psiField.hasModifierProperty(PsiModifier.PRIVATE)) {
        builder.addErrorMessage("inspection.message.lazy.requires.field.to.be.private.final")
          .withLocalQuickFixes(() -> PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.PRIVATE, true, false),
                               () -> PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, true, false));
        result = false;
      }
      if (!psiField.hasInitializer()) {
        builder.addErrorMessage("inspection.message.lazy.requires.field.initialization");
        result = false;
      }
    }

    validateOnXAnnotations(psiAnnotation, psiField, builder, "onMethod");

    if (result) {
      result = validateExistingMethods(psiField, builder, true);
    }

    if (result) {
      result = validateAccessorPrefix(psiField, builder);
    }

    return result;
  }

  private static boolean isLazyGetter(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "lazy", false);
  }

  private static boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemSink builder) {
    boolean result = true;
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    if (!accessorsInfo.acceptsFieldName(psiField.getName())) {
      builder.addWarningMessage("inspection.message.not.generating.getter.for.this.field");
      result = false;
    }
    return result;
  }

  @Contract("_,_,_,null -> !null")
  public static @Nullable PsiMethod createGetterMethod(@NotNull PsiField psiField,
                                                       @NotNull PsiClass psiClass,
                                                       @NotNull String methodModifier,
                                                       @Nullable String nameHint) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    final String methodName = LombokUtils.getGetterName(psiField, accessorsInfo);
    if (nameHint != null && !nameHint.equals(methodName)) return null;

    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiField.getManager(), methodName)
      .withMethodReturnType(psiField.getType())
      .withContainingClass(psiClass)
      .withNavigationElement(psiField)
      .withPureContract();
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

    final String blockText = String.format("return %s.%s;", isStatic ? psiClass.getName() : "this", psiField.getName());
    methodBuilder.withBodyText(blockText);

    final LombokLightModifierList modifierList = methodBuilder.getModifierList();

    LombokCopyableAnnotations.copyCopyableAnnotations(psiField, modifierList, LombokCopyableAnnotations.BASE_COPYABLE);
    PsiAnnotation fieldGetterAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.GETTER);
    LombokCopyableAnnotations.copyOnXAnnotations(fieldGetterAnnotation, modifierList, "onMethod");
    if (psiField.isDeprecated()) {
      modifierList.addAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED);
    }

    return methodBuilder;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ;
  }
}
