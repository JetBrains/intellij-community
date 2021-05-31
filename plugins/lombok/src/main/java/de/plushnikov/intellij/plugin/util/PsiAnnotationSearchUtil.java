package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class PsiAnnotationSearchUtil {

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String annotationFQN) {
    return psiModifierListOwner.getAnnotation(annotationFQN);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner, String @NotNull ... annotationFQNs) {
    return Stream.of(annotationFQNs).map(psiModifierListOwner::getAnnotation).filter(Objects::nonNull).findAny().orElse(null);
  }

  public static boolean isAnnotatedWith(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String annotationFQN) {
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

  public static List<PsiAnnotation> findAllAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames) {
    List<PsiAnnotation> result = Collections.emptyList();

    final PsiModifierList psiModifierList = listOwner.getModifierList();
    if (psiModifierList != null && !annotationNames.isEmpty()) {
      result = new ArrayList<>();
      for (PsiAnnotation annotation : psiModifierList.getAnnotations()) {
        if (ContainerUtil.exists(annotationNames, annotation::hasQualifiedName)) {
          result.add(annotation);
        }
      }
    }
    return result;
  }

  @NotNull
  public static String getShortNameOf(@NotNull PsiAnnotation psiAnnotation) {
    PsiJavaCodeReferenceElement referenceElement = psiAnnotation.getNameReferenceElement();
    return StringUtil.notNullize(null == referenceElement ? null : referenceElement.getReferenceName());
  }

  public static boolean checkAnnotationsSimpleNameExistsIn(@NotNull PsiModifierListOwner modifierListOwner,
                                                           @NotNull Collection<String> annotationNames) {
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        final String shortName = getShortNameOf(psiAnnotation);
        if (annotationNames.contains(shortName)) {
          return true;
        }
      }
    }
    return false;
  }
}
