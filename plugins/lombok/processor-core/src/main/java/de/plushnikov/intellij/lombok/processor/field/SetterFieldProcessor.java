package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
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
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a field
 * Creates setter method for this field
 *
 * @author Plushnikov Michail
 */
public class SetterFieldProcessor extends AbstractLombokFieldProcessor {

  public SetterFieldProcessor() {
    this(Setter.class, PsiMethod.class);
  }

  protected SetterFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  protected void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.add(createSetterMethod(psiField, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;
    result = validateFinalModifier(psiAnnotation, psiField, builder);
    if (result) {
      result = validateVisibility(psiAnnotation);
      if (result) {
        result = validateExistingMethods(psiField, builder);
        if (result) {
          result = validateAccessorPrefix(psiField, builder);
        }
      }
    }
    return result;
  }

  protected boolean validateFinalModifier(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiField.hasModifierProperty(PsiModifier.FINAL)) {
      builder.addError(String.format("'@%s' on final field is not allowed", psiAnnotation.getQualifiedName()),
          PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final Collection<String> methodNames = getAllSetterNames(psiField, isBoolean);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 1)) {
          final String setterMethodName = getSetterName(psiField, isBoolean);

          builder.addWarning(String.format("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName));
          result = false;
        }
      }
    }
    return result;
  }

  protected boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (!AccessorsInfo.build(psiField).prefixDefinedAndStartsWith(psiField.getName())) {
      builder.addWarning("Not generating setter for this field: It does not fit your @Accessors prefix list.");
      result = false;
    }
    return result;
  }

  public List<String> getAllSetterNames(@NotNull PsiField psiField, boolean isBoolean) {
    return LombokUtils.toAllSetterNames(psiField.getName(), isBoolean);
  }

  protected String getSetterName(@NotNull PsiField psiField, boolean isBoolean) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);

    final String fieldNameWithoutPrefix = accessorsInfo.removePrefix(psiField.getName());
    if (accessorsInfo.isFluent()) {
      return LombokUtils.decapitalize(fieldNameWithoutPrefix);
    }
    return LombokUtils.toSetterName(fieldNameWithoutPrefix, isBoolean);
  }

  @NotNull
  public PsiMethod createSetterMethod(@NotNull PsiField psiField, @NotNull String methodModifier) {
    final String fieldName = psiField.getName();
    final PsiType psiFieldType = psiField.getType();

    final String methodName = getSetterName(psiField, PsiType.BOOLEAN.equals(psiFieldType));

    PsiClass psiClass = psiField.getContainingClass();
    assert psiClass != null;

    UserMapKeys.addWriteUsageFor(psiField);

    PsiType returnType = getReturnType(psiField);
    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), methodName)
        .withMethodReturnType(returnType)
        .withContainingClass(psiClass)
        .withParameter(fieldName, psiFieldType)
        .withNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      method.withModifier(methodModifier);
    }
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      method.withModifier(PsiModifier.STATIC);
    }

    PsiParameter methodParameter = method.getParameterList().getParameters()[0];
    PsiModifierList methodParameterModifierList = methodParameter.getModifierList();
    if (null != methodParameterModifierList) {
      final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField,
          LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN);
      for (String annotationFQN : annotationsToCopy) {
        methodParameterModifierList.addAnnotation(annotationFQN);
      }
    }

    copyAnnotations(psiField, method.getModifierList(), LombokUtils.DEPRECATED_PATTERN);

    return method;
  }

  protected PsiType getReturnType(@NotNull PsiField psiField) {
    PsiType result = PsiType.VOID;
    if (!psiField.hasModifierProperty(PsiModifier.STATIC) && AccessorsInfo.build(psiField).isChain()) {
      final PsiClass fieldClass = psiField.getContainingClass();
      if (null != fieldClass) {
        result = PsiClassUtil.getTypeWithGenerics(fieldClass);
      }
    }
    return result;
  }
}
