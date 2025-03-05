package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public final class PsiAnnotationSearchUtil {

  public static @Nullable PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String annotationFQN) {
    if (DumbIncompleteModeUtil.isDumbOrIncompleteMode(psiModifierListOwner)) {
      return DumbIncompleteModeUtil.findAnnotationInDumbOrIncompleteMode(psiModifierListOwner, annotationFQN);
    }
    return psiModifierListOwner.getAnnotation(annotationFQN);
  }

  public static @Nullable PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, String @NotNull ... annotationFQNs) {
    boolean isDumbMode = DumbIncompleteModeUtil.isDumbOrIncompleteMode(psiModifierListOwner);
    for (String annotationFQN : annotationFQNs) {
      PsiAnnotation annotation;
      if (isDumbMode) {
        annotation = DumbIncompleteModeUtil.findAnnotationInDumbOrIncompleteMode(psiModifierListOwner, annotationFQN);
      }
      else {
        annotation = psiModifierListOwner.getAnnotation(annotationFQN);
      }
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String annotationFQN) {
    if (DumbIncompleteModeUtil.isDumbOrIncompleteMode(psiModifierListOwner)) {
      return DumbIncompleteModeUtil.findAnnotationInDumbOrIncompleteMode(psiModifierListOwner, annotationFQN) != null;
    }
    return psiModifierListOwner.hasAnnotation(annotationFQN);
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, String annotationTypeName) {
    return !isAnnotatedWith(psiModifierListOwner, annotationTypeName);
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, String @NotNull ... annotationTypes) {
    return null != findAnnotation(psiModifierListOwner, annotationTypes);
  }

  public static boolean isNotAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, String @NotNull ... annotationTypes) {
    return !isAnnotatedWith(psiModifierListOwner, annotationTypes);
  }

  public static @NotNull String getShortNameOf(@NotNull PsiAnnotation psiAnnotation) {
    PsiJavaCodeReferenceElement referenceElement = psiAnnotation.getNameReferenceElement();
    return StringUtil.notNullize(null == referenceElement ? null : referenceElement.getReferenceName());
  }

  public static boolean checkAnnotationsSimpleNameExistsIn(@NotNull PsiModifierListOwner modifierListOwner,
                                                           @NotNull Collection<String> annotationNames) {
    for (PsiAnnotation psiAnnotation : modifierListOwner.getAnnotations()) {
      final String shortName = getShortNameOf(psiAnnotation);
      if (annotationNames.contains(shortName)) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable PsiAnnotation findAnnotationByShortNameOnly(@NotNull PsiModifierListOwner psiModifierListOwner,
                                                                      String @NotNull ... annotationFQNs) {
    if (annotationFQNs.length > 0) {
      Collection<String> possibleShortNames = ContainerUtil.map(annotationFQNs, StringUtil::getShortName);

      for (PsiAnnotation psiAnnotation : psiModifierListOwner.getAnnotations()) {
        String shortNameOfAnnotation = getShortNameOf(psiAnnotation);
        if(possibleShortNames.contains(shortNameOfAnnotation)) {
          return psiAnnotation;
        }
      }
    }
    return null;
  }

  public static boolean checkAnnotationHasOneOfFQNs(@NotNull PsiAnnotation psiAnnotation,
                                                    String @NotNull ... annotationFQNs) {
    if (DumbIncompleteModeUtil.isDumbOrIncompleteMode(psiAnnotation)) {
      return ContainerUtil.or(annotationFQNs, fqn-> DumbIncompleteModeUtil.hasQualifiedNameInDumbOrIncompleteMode(psiAnnotation, fqn));
    }
    return ContainerUtil.or(annotationFQNs, psiAnnotation::hasQualifiedName);
  }

  public static boolean checkAnnotationHasOneOfFQNs(@NotNull PsiAnnotation psiAnnotation,
                                                    @NotNull Set<String> annotationFQNs) {
    if (DumbIncompleteModeUtil.isDumbOrIncompleteMode(psiAnnotation)) {
      return ContainerUtil.or(annotationFQNs, fqn -> DumbIncompleteModeUtil.hasQualifiedNameInDumbOrIncompleteMode(psiAnnotation, fqn));
    }
    return ContainerUtil.or(annotationFQNs, psiAnnotation::hasQualifiedName);
  }
}
