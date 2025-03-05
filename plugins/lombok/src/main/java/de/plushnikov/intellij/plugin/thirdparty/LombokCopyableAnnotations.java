package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public enum LombokCopyableAnnotations {
  BASE_COPYABLE(LombokUtils.BASE_COPYABLE_ANNOTATIONS),
  COPY_TO_SETTER(LombokUtils.COPY_TO_SETTER_ANNOTATIONS),
  COPY_TO_BUILDER_SINGULAR_SETTER(LombokUtils.COPY_TO_BUILDER_SINGULAR_SETTER_ANNOTATIONS),
  JACKSON_COPY_TO_BUILDER(LombokUtils.JACKSON_COPY_TO_BUILDER_ANNOTATIONS);

  private final Map<String, Set<String>> shortNames;

  LombokCopyableAnnotations(String[] fqns) {
    shortNames = new HashMap<>(fqns.length);
    for (String fqn : fqns) {
      String shortName = StringUtil.getShortName(fqn);
      shortNames.computeIfAbsent(shortName, __ -> new HashSet<>(5)).add(fqn);
    }
  }

  public static void copyOnXAnnotations(@Nullable PsiAnnotation processedAnnotation,
                                        @NotNull PsiModifierList modifierList,
                                        @NotNull String onXParameterName) {
    if (processedAnnotation == null) {
      return;
    }

    Iterable<String> annotationsToAdd = LombokProcessorUtil.getOnX(processedAnnotation, onXParameterName);
    annotationsToAdd.forEach(modifierList::addAnnotation);
  }

  public @NotNull List<PsiAnnotation> collectCopyableAnnotations(@NotNull PsiModifierListOwner psiFromElement,
                                                                 @Nullable PsiClass containingClass) {
    final PsiAnnotation[] fieldAnnotations = psiFromElement.getAnnotations();
    if (0 == fieldAnnotations.length) {
      // nothing to copy if no annotations defined
      return Collections.emptyList();
    }

    final Set<String> annotationNames = new HashSet<>();
    final Collection<String> existedShortAnnotationNames = ContainerUtil.map2Set(fieldAnnotations, PsiAnnotationSearchUtil::getShortNameOf);

    for (String shortName : existedShortAnnotationNames) {
      Set<String> fqns = shortNames.get(shortName);
      if (fqns != null) {
        annotationNames.addAll(fqns);
      }
    }

    // append only for BASE_COPYABLE
    if (BASE_COPYABLE.equals(this) && null != containingClass) {
      Collection<String> configuredCopyableAnnotations =
        ConfigDiscovery.getInstance().getMultipleValueLombokConfigProperty(ConfigKey.COPYABLE_ANNOTATIONS, containingClass);

      for (String fqn : configuredCopyableAnnotations) {
        if (existedShortAnnotationNames.contains(StringUtil.getShortName(fqn))) {
          annotationNames.add(fqn);
        }
      }
    }

    if (annotationNames.isEmpty()) {
      return Collections.emptyList();
    }

    List<PsiAnnotation> result = new ArrayList<>();
    for (PsiAnnotation annotation : fieldAnnotations) {
      if (PsiAnnotationSearchUtil.checkAnnotationHasOneOfFQNs(annotation, annotationNames)) {
        result.add(annotation);
      }
    }
    return result;
  }

  public static <T extends PsiModifierListOwner & PsiMember> void copyCopyableAnnotations(@NotNull T fromPsiElement,
                                                                                          @NotNull LombokLightModifierList toModifierList,
                                                                                          @NotNull LombokCopyableAnnotations copyableAnnotations) {
    copyCopyableAnnotations(fromPsiElement, fromPsiElement.getContainingClass(), toModifierList, copyableAnnotations);
  }

  public static void copyCopyableAnnotations(@NotNull PsiModifierListOwner fromPsiElement,
                                             @Nullable PsiClass containingClass,
                                             @NotNull LombokLightModifierList toModifierList,
                                             @NotNull LombokCopyableAnnotations copyableAnnotations) {
    List<PsiAnnotation> annotationsToAdd = copyableAnnotations.collectCopyableAnnotations(fromPsiElement, containingClass);
    annotationsToAdd.forEach(toModifierList::withAnnotation);
  }
}
