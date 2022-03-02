package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Base lombok processor class
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractProcessor implements Processor {
  /**
   * Annotation classes this processor supports
   */
  private final String[] supportedAnnotationClasses;
  /**
   * Kind of output elements this processor supports
   */
  private final Class<? extends PsiElement> supportedClass;
  /**
   * Instance of config discovery service to access lombok.config informations
   */
  protected final ConfigDiscovery configDiscovery;

  /**
   * Constructor for all Lombok-Processors
   *
   * @param supportedClass             kind of output elements this processor supports
   * @param supportedAnnotationClasses annotations this processor supports
   */
  protected AbstractProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                              @NotNull String... supportedAnnotationClasses) {
    this.configDiscovery = ConfigDiscovery.getInstance();
    this.supportedClass = supportedClass;
    this.supportedAnnotationClasses = supportedAnnotationClasses;
  }

  @Override
  public final @NotNull String @NotNull [] getSupportedAnnotationClasses() {
    return supportedAnnotationClasses;
  }

  @NotNull
  @Override
  public final Class<? extends PsiElement> getSupportedClass() {
    return supportedClass;
  }

  @NotNull
  public abstract Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass);

  protected boolean supportAnnotationVariant(@NotNull PsiAnnotation psiAnnotation) {
    return true;
  }

  protected void filterToleratedElements(@NotNull Collection<? extends PsiModifierListOwner> definedMethods) {
    definedMethods.removeIf(definedMethod -> PsiAnnotationSearchUtil.isAnnotatedWith(definedMethod, LombokClassNames.TOLERATE));
  }

  protected boolean readAnnotationOrConfigProperty(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass,
                                                   @NotNull String annotationParameter, @NotNull ConfigKey configKey) {
    final boolean result;
    final Boolean declaredAnnotationValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(psiAnnotation, annotationParameter);
    if (null == declaredAnnotationValue) {
      result = configDiscovery.getBooleanLombokConfigProperty(configKey, psiClass);
    }
    else {
      result = declaredAnnotationValue;
    }
    return result;
  }

  protected static void copyOnXAnnotations(@Nullable PsiAnnotation processedAnnotation,
                                           @NotNull PsiModifierList modifierList,
                                           @NotNull String onXParameterName) {
    if (processedAnnotation == null) {
      return;
    }

    Iterable<String> annotationsToAdd = LombokProcessorUtil.getOnX(processedAnnotation, onXParameterName);
    annotationsToAdd.forEach(modifierList::addAnnotation);
  }

  protected static @NotNull List<PsiAnnotation> copyableAnnotations(@NotNull PsiField psiField,
                                                                    @NotNull LombokCopyableAnnotations copyableAnnotations) {
    final PsiAnnotation[] fieldAnnotations = psiField.getAnnotations();
    if (0 == fieldAnnotations.length) {
      // nothing to copy if no annotations defined
      return Collections.emptyList();
    }

    @NotNull Set<String> annotationNames = new HashSet<>();

    final Collection<String> existedShortAnnotationNames = ContainerUtil.map2Set(fieldAnnotations, PsiAnnotationSearchUtil::getShortNameOf);

    Map<String, Set<String>> shortNames = copyableAnnotations.getShortNames();
    for (String shortName : existedShortAnnotationNames) {
      Set<String> fqns = shortNames.get(shortName);
      if (fqns != null) {
        annotationNames.addAll(fqns);
      }
    }

    final PsiClass containingClass = psiField.getContainingClass();
    // append only for BASE_COPYABLE
    if (LombokCopyableAnnotations.BASE_COPYABLE.equals(copyableAnnotations) && null != containingClass) {
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
      if (ContainerUtil.exists(annotationNames, annotation::hasQualifiedName)) {
        result.add(annotation);
      }
    }
    return result;
  }

  protected static void copyCopyableAnnotations(@NotNull PsiField fromPsiElement,
                                                @NotNull LombokLightModifierList toModifierList,
                                                @NotNull LombokCopyableAnnotations copyableAnnotations) {
    List<PsiAnnotation> annotationsToAdd = copyableAnnotations(fromPsiElement, copyableAnnotations);
    annotationsToAdd.forEach(toModifierList::withAnnotation);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.NONE;
  }
}
