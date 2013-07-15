package de.plushnikov.intellij.lombok.processor.field;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.LombokUtils;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.lombok.util.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Getter;

/**
 * Inspect and validate @Getter lombok annotation on a field
 * Creates getter method for this field
 *
 * @author Plushnikov Michail
 */
public class GetterFieldProcessor extends AbstractLombokFieldProcessor {

  public GetterFieldProcessor() {
    super(Getter.class, PsiMethod.class);
  }

  protected GetterFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.add((Psi) createGetterMethod(psiField, methodVisibility));
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

    return result;
  }

  protected boolean isLazyGetter(@NotNull PsiAnnotation psiAnnotation) {
    final Boolean lazyObj = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "lazy", Boolean.class);
    return null != lazyObj && lazyObj;
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final Collection<String> methodNames = LombokUtils.toAllGetterNames(psiField.getName(), isBoolean);
      final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0)) {
          final String setterMethodName = LombokUtils.toGetterName(psiField.getName(), isBoolean);

          builder.addWarning(String.format("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName));
          result = false;
        }
      }
    }
    return result;
  }

  @NotNull
  public PsiMethod createGetterMethod(@NotNull PsiField psiField, @NotNull String methodModifier) {
    final String fieldName = psiField.getName();
    final PsiType psiReturnType = psiField.getType();

    String methodName = LombokUtils.toGetterName(fieldName, PsiType.BOOLEAN.equals(psiReturnType));

    PsiClass psiClass = psiField.getContainingClass();
    assert psiClass != null;

    UserMapKeys.addReadUsageFor(psiField);

    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), methodName)
        .withMethodReturnType(psiReturnType)
        .withContainingClass(psiClass)
        .withNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      method.withModifier(methodModifier);
    }
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      method.withModifier(PsiModifier.STATIC);
    }

    copyAnnotations(psiField, method.getModifierList(),
        LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN, LombokUtils.DEPRECATED_PATTERN);
    return method;
  }

}
