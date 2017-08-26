package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.regex.Pattern;

public class PsiAnnotationSearchUtil {
  private static final Key<String> LOMBOK_ANNOTATION_FQN_KEY = Key.create("LOMBOK_ANNOTATION_FQN");

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String annotationFQN) {
    return findAnnotationQuick(psiModifierListOwner.getModifierList(), annotationFQN);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    return findAnnotationQuick(psiModifierListOwner.getModifierList(), annotationType.getName());
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation>... annotationTypes) {
    final String[] qualifiedNames = new String[annotationTypes.length];
    for (int i = 0; i < annotationTypes.length; i++) {
      qualifiedNames[i] = annotationTypes[i].getName();
    }
    return findAnnotationQuick(psiModifierListOwner.getModifierList(), qualifiedNames);
  }

  @Nullable
  private static PsiAnnotation findAnnotationQuick(@Nullable PsiAnnotationOwner annotationOwner, @NotNull String... qualifiedNames) {
    if (annotationOwner == null || qualifiedNames.length == 0) {
      return null;
    }

    PsiAnnotation[] annotations = annotationOwner.getAnnotations();
    if (annotations.length == 0) {
      return null;
    }

    final String[] shortNames = new String[qualifiedNames.length];
    for (int i = 0; i < qualifiedNames.length; i++) {
      shortNames[i] = StringUtil.getShortName(qualifiedNames[i]);
    }

    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null && ArrayUtil.find(shortNames, referenceElement.getReferenceName()) > -1) {

        String annotationQualifiedName = annotation.getCopyableUserData(LOMBOK_ANNOTATION_FQN_KEY);
        if (null == annotationQualifiedName) {
          annotationQualifiedName = annotation.getQualifiedName();
          if (null != annotationQualifiedName && annotationQualifiedName.indexOf('.') > -1) {
            annotation.putCopyableUserData(LOMBOK_ANNOTATION_FQN_KEY, annotationQualifiedName);
          }
        }

        if (ArrayUtil.find(qualifiedNames, annotationQualifiedName) > -1) {
          return annotation;
        }
      }
    }

    return null;
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    return null != findAnnotation(psiModifierListOwner, annotationType);
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation> annotationType) {
    return !isAnnotatedWith(psiModifierListOwner, annotationType);
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final Class<? extends Annotation>... annotationTypes) {
    return null != findAnnotation(psiModifierListOwner, annotationTypes);
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

  public static boolean checkAnnotationsSimpleNameExistsIn(@NotNull PsiModifierListOwner modifierListOwner, @NotNull Collection<String> annotationNames) {
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        final String simpleName = getSimpleNameOf(psiAnnotation);
        if (annotationNames.contains(simpleName)) {
          return true;
        }
      }
    }
    return false;
  }
}
