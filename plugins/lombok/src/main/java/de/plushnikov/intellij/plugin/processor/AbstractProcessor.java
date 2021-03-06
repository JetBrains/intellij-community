package de.plushnikov.intellij.plugin.processor;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

  protected static List<String> copyableAnnotations(@NotNull PsiField psiField, final List<String> copyableAnnotations) {
    final List<String> combinedListOfCopyableAnnotations = new ArrayList<>(copyableAnnotations);

    final PsiClass containingClass = psiField.getContainingClass();
    // append only for BASE_COPYABLE
    if (copyableAnnotations == LombokUtils.BASE_COPYABLE_ANNOTATIONS && null != containingClass) {
      String[] configuredCopyableAnnotations =
        ConfigDiscovery.getInstance().getMultipleValueLombokConfigProperty(ConfigKey.COPYABLE_ANNOTATIONS, containingClass);
      combinedListOfCopyableAnnotations.addAll(Arrays.asList(configuredCopyableAnnotations));
    }

    final List<String> existingAnnotations = ContainerUtil.map(psiField.getAnnotations(), PsiAnnotation::getQualifiedName);
    existingAnnotations.retainAll(combinedListOfCopyableAnnotations);

    return existingAnnotations;
  }

  protected static void copyCopyableAnnotations(@NotNull PsiField fromPsiElement,
                                                @NotNull PsiModifierList toModifierList,
                                                List<String> copyableAnnotations) {
    List<String> existingAnnotations = copyableAnnotations(fromPsiElement, copyableAnnotations);

    for (String annotation : existingAnnotations) {
      PsiAnnotation srcAnnotation = AnnotationUtil.findAnnotation(fromPsiElement, annotation);
      PsiNameValuePair[] valuePairs =
        srcAnnotation != null ? srcAnnotation.getParameterList().getAttributes() : PsiNameValuePair.EMPTY_ARRAY;
      AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(annotation, valuePairs, toModifierList);
    }
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.NONE;
  }
}
