package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.regex.Pattern;

public final class PsiAnnotationSearchUtil {
  private static final Key<String> LOMBOK_ANNOTATION_FQN_KEY = Key.create("LOMBOK_ANNOTATION_FQN");

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String annotationFQN) {
    return findAnnotationQuick(psiModifierListOwner.getModifierList(), annotationFQN);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String... annotationFQNs) {
    return findAnnotationQuick(psiModifierListOwner.getModifierList(), annotationFQNs);
  }

  @Nullable
  private static PsiAnnotation findAnnotationQuick(@Nullable PsiAnnotationOwner annotationOwner, @NotNull String qualifiedName) {
    if (annotationOwner == null) {
      return null;
    }

    PsiAnnotation[] annotations = annotationOwner.getAnnotations();
    if (annotations.length == 0) {
      return null;
    }

    final String shortName = StringUtil.getShortName(qualifiedName);

    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (null != referenceElement) {
        final String referenceName = referenceElement.getReferenceName();
        if (shortName.equals(referenceName)) {

          if (referenceElement.isQualified() && referenceElement instanceof SourceJavaCodeReference) {
            String possibleFullQualifiedName = ((SourceJavaCodeReference) referenceElement).getClassNameText();
            if (qualifiedName.equals(possibleFullQualifiedName)) {
              return annotation;
            }
          }

          final String annotationQualifiedName = getAndCacheFQN(annotation, referenceName);
          if (null != annotationQualifiedName && qualifiedName.endsWith(annotationQualifiedName)) {
            return annotation;
          }
        }
      }
    }

    return null;
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
      final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (null != referenceElement) {
        final String referenceName = referenceElement.getReferenceName();
        if (ArrayUtil.find(shortNames, referenceName) > -1) {

          if (referenceElement.isQualified() && referenceElement instanceof SourceJavaCodeReference) {
            final String possibleFullQualifiedName = ((SourceJavaCodeReference) referenceElement).getClassNameText();

            if (ArrayUtil.find(qualifiedNames, possibleFullQualifiedName) > -1) {
              return annotation;
            }
          }

          final String annotationQualifiedName = getAndCacheFQN(annotation, referenceName);
          if (ArrayUtil.find(qualifiedNames, annotationQualifiedName) > -1) {
            return annotation;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static String getAndCacheFQN(@NotNull PsiAnnotation annotation, @Nullable String referenceName) {
    String annotationQualifiedName = annotation.getCopyableUserData(LOMBOK_ANNOTATION_FQN_KEY);
    // if not cached or cache is not up to date (because existing annotation was renamed for example)
    if (null == annotationQualifiedName || (null != referenceName && !annotationQualifiedName.endsWith(".".concat(referenceName)))) {
      annotationQualifiedName = annotation.getQualifiedName();
      if (null != annotationQualifiedName && annotationQualifiedName.indexOf('.') > -1) {
        annotation.putCopyableUserData(LOMBOK_ANNOTATION_FQN_KEY, annotationQualifiedName);
      }
    }
    return annotationQualifiedName;
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String annotationTypeName) {
    return null != findAnnotation(psiModifierListOwner, annotationTypeName);
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, String annotationTypeName) {
    return !isAnnotatedWith(psiModifierListOwner, annotationTypeName);
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final String... annotationTypes) {
    return null != findAnnotation(psiModifierListOwner, annotationTypes);
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull final String... annotationTypes) {
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
