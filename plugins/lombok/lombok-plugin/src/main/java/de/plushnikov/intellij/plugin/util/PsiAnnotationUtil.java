package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * @author peichhorn
 * @author Plushnikov Michail
 */
public class PsiAnnotationUtil {

  @NotNull
  public static PsiAnnotation createPsiAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull Class<? extends Annotation> annotationClass) {
    return createPsiAnnotation(psiModifierListOwner, annotationClass, "");
  }

  @NotNull
  public static PsiAnnotation createPsiAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull Class<? extends Annotation> annotationClass, @Nullable String value) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiModifierListOwner.getProject()).getElementFactory();
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiModifierListOwner, PsiClass.class);
    final String valueString = StringUtil.isNotEmpty(value) ? "(" + value + ")" : "";
    return elementFactory.createAnnotationFromText("@" + annotationClass.getName() + valueString, psiClass);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    return findAnnotation(psiModifierListOwner, annotationType.getName());
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String qualifiedName) {
    final PsiModifierList annotationOwner = psiModifierListOwner.getModifierList();
    if (annotationOwner == null) {
      return null;
    }

    final PsiAnnotation[] annotations = annotationOwner.getAnnotations();
    if (annotations.length == 0) {
      return null;
    }

    final String shortName = StringUtil.getShortName(qualifiedName);
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null && shortName.equals(referenceElement.getReferenceName())) {
        if (qualifiedName.equals(annotation.getQualifiedName())) {
          return annotation;
        }
      }
    }

    return null;
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    return null != findAnnotation(psiModifierListOwner, annotationType.getName());
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    return !isAnnotatedWith(psiModifierListOwner, annotationType);
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation>... annotationTypes) {
    final PsiModifierList annotationOwner = psiModifierListOwner.getModifierList();
    if (annotationOwner == null) {
      return false;
    }

    final PsiAnnotation[] annotations = annotationOwner.getAnnotations();
    if (annotations.length == 0) {
      return false;
    }

    for (Class<? extends Annotation> annotationType : annotationTypes) {
      final String shortName = annotationType.getSimpleName();
      for (PsiAnnotation annotation : annotations) {
        PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
        if (referenceElement != null && shortName.equals(referenceElement.getReferenceName())) {
          String qualifiedName = annotationType.getName();
          if (qualifiedName.equals(annotation.getQualifiedName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation>... annotationTypes) {
    return !isAnnotatedWith(psiModifierListOwner, annotationTypes);
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Pattern annotationPattern) {
    final PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();
    if (psiModifierList != null) {
      for (PsiAnnotation psiAnnotation : psiModifierList.getAnnotations()) {
        final String suspect = getSimpleNameOf(psiAnnotation);
        if (annotationPattern.matcher(suspect).matches()) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static String getSimpleNameOf(@NotNull PsiAnnotation psiAnnotation) {
    PsiJavaCodeReferenceElement referenceElement = psiAnnotation.getNameReferenceElement();
    return StringUtil.notNullize(null == referenceElement ? null : referenceElement.getReferenceName());
  }


  @Nullable
  public static <T> T getAnnotationValue(@NotNull PsiAnnotation psiAnnotation, Class<T> asClass) {
    return getAnnotationValue(psiAnnotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, asClass);
  }

  @NotNull
  public static <T> Collection<T> getAnnotationValues(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter, @NotNull Class<T> asClass) {
    Collection<T> result = Collections.emptyList();
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findAttributeValue(parameter);
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] memberValues = ((PsiArrayInitializerMemberValue) attributeValue).getInitializers();
      result = new ArrayList<T>(memberValues.length);

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

  public static <T> T getAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter, @NotNull Class<T> asClass, @NotNull T defaultValue) {
    T result = getAnnotationValue(psiAnnotation, parameter, asClass);
    return result == null ? defaultValue : result;
  }

  @Nullable
  public static <T> T getAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter, @NotNull Class<T> asClass) {
    T result = null;
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findAttributeValue(parameter);
    if (null != attributeValue) {
      result = resolveElementValue(attributeValue, asClass);
    }
    return result;
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
    }
    return value;
  }

  @Nullable
  public static <T> T getDeclaredAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter, @NotNull Class<T> asClass) {
    T value = null;
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findDeclaredAttributeValue(parameter);
    if (null != attributeValue) {
      value = resolveElementValue(attributeValue, asClass);
    }
    return value;
  }

  @NotNull
  public static Collection<String> collectAnnotationsToCopy(@NotNull PsiField psiField, final Pattern... patterns) {
    Collection<String> annotationsToCopy = new ArrayList<String>();
    PsiModifierList modifierList = psiField.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        final String annotationName = getSimpleNameOf(psiAnnotation);
        for (Pattern pattern : patterns) {
          if (pattern.matcher(annotationName).matches()) {
            annotationsToCopy.add(psiAnnotation.getQualifiedName());
          }
        }
      }
    }
    return annotationsToCopy;
  }

  public static String extractAnnotationSimpleName(@NotNull PsiAnnotation psiAnnotation) {
    final String psiAnnotationText = psiAnnotation.getText();
    int from = 0;
    int to = psiAnnotationText.length();
    if (psiAnnotationText.indexOf('@') == 0) {
      from++;
    }
    int indexOf = psiAnnotationText.indexOf('(');
    if (indexOf > 0) {
      to = indexOf;
    }
    return psiAnnotationText.substring(from, to).trim();
  }

  public static boolean checkAnnotationsSimpleNameExistsIn(@NotNull PsiModifierListOwner modifierListOwner, @NotNull Collection<String> annotationNames) {
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        if (annotationNames.contains(extractAnnotationSimpleName(psiAnnotation))) {
          return true;
        }
      }
    }
    return false;
  }
}
