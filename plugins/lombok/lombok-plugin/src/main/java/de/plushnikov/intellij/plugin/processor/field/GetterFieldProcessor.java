package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Getter lombok annotation on a field
 * Creates getter method for this field
 *
 * @author Plushnikov Michail
 */
public class GetterFieldProcessor extends AbstractFieldProcessor {

  public GetterFieldProcessor() {
    super(Getter.class, PsiMethod.class);
  }

  protected GetterFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != methodVisibility && null != psiClass) {
      target.add(createGetterMethod(psiField, psiClass, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;

    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    result = null != methodVisibility;

    final boolean lazy = isLazyGetter(psiAnnotation);
    if (null == methodVisibility && lazy) {
      builder.addWarning("'lazy' does not work with AccessLevel.NONE.");
    }

    if (result && lazy) {
      if (!psiField.hasModifierProperty(PsiModifier.FINAL) || !psiField.hasModifierProperty(PsiModifier.PRIVATE)) {
        builder.addError("'lazy' requires the field to be private and final",
            PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.PRIVATE, true, false),
            PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, true, false));
        result = false;
      }
      if (null == psiField.getInitializer()) {
        builder.addError("'lazy' requires field initialization.");
        result = false;
      }
    }

    if (result) {
      result = validateExistingMethods(psiField, builder);
    }

    if (result) {
      result = validateAccessorPrefix(psiField, builder);
    }

    return result;
  }

  protected boolean isLazyGetter(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "lazy", false);
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
      final Collection<String> methodNames = LombokUtils.toAllGetterNames(accessorsInfo, psiField.getName(), isBoolean);
      final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0)) {
          final String setterMethodName = getGetterName(psiField);

          builder.addWarning("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName);
          result = false;
        }
      }
    }
    return result;
  }

  protected boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (!AccessorsInfo.build(psiField).prefixDefinedAndStartsWith(psiField.getName())) {
      builder.addWarning("Not generating getter for this field: It does not fit your @Accessors prefix list.");
      result = false;
    }
    return result;
  }

  @NotNull
  public PsiMethod createGetterMethod(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull String methodModifier) {
    final String methodName = getGetterName(psiField);

    LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiField.getManager(), methodName)
        .withMethodReturnType(psiField.getType())
        .withContainingClass(psiClass)
        .withNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      method.withModifier(methodModifier);
    }
    boolean isStatic = psiField.hasModifierProperty(PsiModifier.STATIC);
    if (isStatic) {
      method.withModifier(PsiModifier.STATIC);
    }

    method.withBody(PsiMethodUtil.createCodeBlockFromText(String.format("return %s.%s;", isStatic ? psiClass.getName() : "this", psiField.getName()), psiClass));

    PsiModifierList modifierList = method.getModifierList();
    copyAnnotations(psiField, modifierList,
        LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN, LombokUtils.DEPRECATED_PATTERN);
    addOnXAnnotations(PsiAnnotationUtil.findAnnotation(psiField, Getter.class), modifierList, "onMethod");
    return method;
  }
}
