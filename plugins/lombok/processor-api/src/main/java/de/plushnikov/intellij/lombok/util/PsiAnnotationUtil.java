package de.plushnikov.intellij.lombok.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.util.StringBuilderSpinAllocator;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author peichhorn
 * @author Plushnikov Michail
 */
public class PsiAnnotationUtil {
  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation>... annotationTypes) {
    return !isAnnotatedWith(psiModifierListOwner, annotationTypes);
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    return !isAnnotatedWith(psiModifierListOwner, annotationType);
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation>... annotationTypes) {
    final Collection<String> annotationTypeNames = new HashSet<String>();
    for (Class<?> annotationType : annotationTypes) {
      annotationTypeNames.add(annotationType.getName());
    }
    final PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();
    if (psiModifierList != null) {
      for (PsiAnnotation psiAnnotation : psiModifierList.getApplicableAnnotations()) {
        if (annotationTypeNames.contains(psiAnnotation.getQualifiedName())) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    final PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();
    if (psiModifierList != null) {
      for (PsiAnnotation psiAnnotation : psiModifierList.getApplicableAnnotations()) {
        if (annotationType.getName().equals(psiAnnotation.getQualifiedName())) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Pattern annotationPattern) {
    final PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();
    if (psiModifierList != null) {
      for (PsiAnnotation psiAnnotation : psiModifierList.getApplicableAnnotations()) {
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
    final String name = StringUtil.notNullize(psiAnnotation.getQualifiedName());
    final int idx = name.lastIndexOf(".");
    return idx == -1 ? name : name.substring(idx + 1);
  }

  @NotNull
  public static List<PsiAnnotation> findAnnotations(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Pattern annotationPattern) {
    final List<PsiAnnotation> annoations = new ArrayList<PsiAnnotation>();
    final PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();
    if (psiModifierList != null) {
      for (PsiAnnotation psiAnnotation : psiModifierList.getApplicableAnnotations()) {
        final String name = getSimpleNameOf(psiAnnotation);
        if (annotationPattern.matcher(name).matches()) {
          annoations.add(psiAnnotation);
        }
      }
    }
    return annoations;
  }

  @Nullable
  public static String getAnnotationValue(@NotNull PsiAnnotation psiAnnotation) {
    return getAnnotationValue(psiAnnotation, "value");
  }

  @Nullable
  public static String getAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter) {
    String value = null;
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findAttributeValue(parameter);
    if (null != attributeValue) {
      value = resolveElementValue(attributeValue);
    }
    return value;
  }

  @NotNull
  public static String[] getAnnotationValues(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter) {
    String[] value = EMPTY_STRING_ARRAY;
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findAttributeValue(parameter);
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] memberValues = ((PsiArrayInitializerMemberValue) attributeValue).getInitializers();

      value = new String[memberValues.length];
      for (int i = 0; i < memberValues.length; i++) {
        value[i] = resolveElementValue(memberValues[i]);
      }
    }
    return value;
  }

  @Nullable
  public static String getDeclaredAnnotationValue(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter) {
    String value = null;
    PsiAnnotationMemberValue attributeValue = psiAnnotation.findDeclaredAttributeValue(parameter);
    if (null != attributeValue) {
      value = resolveElementValue(attributeValue);
    }
    return value;
  }

  private static String resolveElementValue(PsiElement psiElement) {
    String value = null;
    if (psiElement instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression) psiElement).resolve();

      if (resolved instanceof PsiEnumConstant) {
        final PsiEnumConstant psiEnumConstant = (PsiEnumConstant) resolved;
        value = psiEnumConstant.getName();
      } else if (resolved instanceof PsiVariable) {
        final PsiVariable psiVariable = (PsiVariable) resolved;
        final PsiExpression initializer = psiVariable.getInitializer();
        if (null != initializer) {
          value = resolveElementValue(initializer);
        }
      }
    } else if (psiElement instanceof PsiLiteralExpression) {
      Object elementValue = ((PsiLiteralExpression) psiElement).getValue();
      if (null != elementValue) {
        value = elementValue.toString();
      }
    }
    return value;
  }

  @NotNull
  public static Collection<String> collectAnnotationsToCopy(@NotNull PsiField psiField) {
    Collection<String> annotationsToCopy = new ArrayList<String>();
    PsiModifierList modifierList = psiField.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        final String qualifiedName = StringUtil.notNullize(psiAnnotation.getQualifiedName());
        final String annotationName = extractAnnotationName(qualifiedName);
        if (TransformationsUtil.NON_NULL_PATTERN.matcher(annotationName).matches()) {
          annotationsToCopy.add(qualifiedName);
        }
      }
    }
    return annotationsToCopy;
  }

  @NotNull
  public static String extractAnnotationName(@NotNull String qualifiedName) {
    final String annotationName;
    int indexOfLastPoint = qualifiedName.lastIndexOf('.');
    if (indexOfLastPoint != -1) {
      annotationName = qualifiedName.substring(indexOfLastPoint + 1);
    } else {
      annotationName = qualifiedName;
    }
    return annotationName;
  }

  @NotNull
  public static String buildAnnotationsString(@NotNull Collection<String> annotationsToCopy) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (String annotationName : annotationsToCopy) {
        builder.append('@').append(annotationName).append(' ');
      }
      return builder.toString();
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
