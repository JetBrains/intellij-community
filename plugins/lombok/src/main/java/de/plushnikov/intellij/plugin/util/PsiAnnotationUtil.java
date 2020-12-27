package de.plushnikov.intellij.plugin.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Some util methods for annotation processing
 *
 * @author peichhorn
 * @author Plushnikov Michail
 */
public final class PsiAnnotationUtil {

  @NotNull
  public static PsiAnnotation createPsiAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, String annotationClassName) {
    return createPsiAnnotation(psiModifierListOwner, "", annotationClassName);
  }

  @NotNull
  public static PsiAnnotation createPsiAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner,
                                                  @Nullable String value,
                                                  String annotationClassName) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiModifierListOwner.getProject());
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiModifierListOwner, PsiClass.class);
    final String valueString = StringUtil.isNotEmpty(value) ? "(" + value + ")" : "";
    return elementFactory.createAnnotationFromText("@" + annotationClassName + valueString, psiClass);
  }

  @NotNull
  public static <T> Collection<T> getAnnotationValues(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter, @NotNull Class<T> asClass) {
    Collection<T> result = Collections.emptyList();
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findAttributeValue(parameter);
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] memberValues = ((PsiArrayInitializerMemberValue) attributeValue).getInitializers();
      result = new ArrayList<>(memberValues.length);

      for (PsiAnnotationMemberValue memberValue : memberValues) {
        T value = resolveElementValue(memberValue, asClass);
        if (null != value) {
          result.add(value);
        }
      }
    } else if (null != attributeValue) {
      T value = resolveElementValue(attributeValue, asClass);
      if (null != value) {
        result = Collections.singletonList(value);
      }
    }
    return result;
  }

  public static boolean hasDeclaredProperty(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter) {
    return null != psiAnnotation.findDeclaredAttributeValue(parameter);
  }

  public static boolean getBooleanAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter, boolean defaultValue) {
    final Boolean result = psiAnnotation.hasAttribute(parameter) ? AnnotationUtil.getBooleanAttributeValue(psiAnnotation, parameter) : null;
    return result == null ? defaultValue : result;
  }

  public static String getStringAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter, @NotNull String defaultValue) {
    final String result = AnnotationUtil.getDeclaredStringAttributeValue(psiAnnotation, parameter);
    return result != null ? result : defaultValue;
  }

  public static String getEnumAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String attributeName, @NotNull String defaultValue) {
    PsiAnnotationMemberValue attrValue = psiAnnotation.findDeclaredAttributeValue(attributeName);
    String result = attrValue != null ? resolveElementValue(attrValue, String.class) : null;
    return result != null ? result : defaultValue;
  }

  public static int getIntAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String attributeName, int defaultValue) {
    PsiAnnotationMemberValue attrValue = psiAnnotation.findDeclaredAttributeValue(attributeName);
    PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(psiAnnotation.getProject()).getConstantEvaluationHelper();
    Object result = evaluationHelper.computeConstantExpression(attrValue);
    return result instanceof Number ? ((Number) result).intValue() : defaultValue;
  }

  @Nullable
  private static <T> T resolveElementValue(@NotNull PsiElement psiElement, @NotNull Class<T> asClass) {
    T value = null;
    if (psiElement instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression) psiElement).resolve();

      if (resolved instanceof PsiEnumConstant) {
        final PsiEnumConstant psiEnumConstant = (PsiEnumConstant) resolved;
        //Enums are supported as VALUE-Strings only
        if (asClass.isAssignableFrom(String.class)) {
          value = (T) psiEnumConstant.getName();
        }
      } else if (resolved instanceof PsiVariable) {
        final PsiVariable psiVariable = (PsiVariable) resolved;
        Object elementValue = psiVariable.computeConstantValue();
        if (null != elementValue && asClass.isAssignableFrom(elementValue.getClass())) {
          value = (T) elementValue;
        }
      }
    } else if (psiElement instanceof PsiLiteralExpression) {
      Object elementValue = ((PsiLiteralExpression) psiElement).getValue();
      if (null != elementValue && asClass.isAssignableFrom(elementValue.getClass())) {
        value = (T) elementValue;
      }
    } else if (psiElement instanceof PsiClassObjectAccessExpression) {
      PsiTypeElement elementValue = ((PsiClassObjectAccessExpression) psiElement).getOperand();
      //Enums are supported as VALUE-Strings only
      if (asClass.isAssignableFrom(PsiType.class)) {
        value = (T) elementValue.getType();
      }
    } else if (psiElement instanceof PsiAnnotation) {
      if (asClass.isAssignableFrom(PsiAnnotation.class)) {
        value = (T) psiElement;
      }
    } else if (psiElement instanceof PsiPrefixExpression) {
      if (asClass.isAssignableFrom(String.class)) {
        String expressionText = psiElement.getText();
        value = (T) expressionText;
      }
    }
    return value;
  }

  @Nullable
  public static Boolean getDeclaredBooleanAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter) {
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findDeclaredAttributeValue(parameter);
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(psiAnnotation.getProject());
    Object constValue = javaPsiFacade.getConstantEvaluationHelper().computeConstantExpression(attributeValue);
    return constValue instanceof Boolean ? (Boolean) constValue : null;
  }

  @NotNull
  public static Collection<String> collectAnnotationsToCopy(@NotNull PsiField psiField, final Pattern... patterns) {
    Collection<String> annotationsToCopy = new ArrayList<>();
    PsiModifierList modifierList = psiField.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        final String annotationName = PsiAnnotationSearchUtil.getSimpleNameOf(psiAnnotation);
        for (Pattern pattern : patterns) {
          if (pattern.matcher(annotationName).matches()) {
            annotationsToCopy.add(psiAnnotation.getQualifiedName());
          }
        }
      }
    }
    return annotationsToCopy;
  }

}
