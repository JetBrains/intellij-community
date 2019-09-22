package de.plushnikov.intellij.plugin.processor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.Tolerate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;

/**
 * Base lombok processor class
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractProcessor implements Processor {
  /**
   * Annotation classes this processor supports
   */
  private final Class<? extends Annotation>[] supportedAnnotationClasses;
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
   *  @param supportedClass           kind of output elements this processor supports
   * @param supportedAnnotationClass annotation this processor supports
   */
  @SuppressWarnings("unchecked")
  protected AbstractProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                              @NotNull Class<? extends Annotation> supportedAnnotationClass) {
    this.configDiscovery = ConfigDiscovery.getInstance();
    this.supportedClass = supportedClass;
    this.supportedAnnotationClasses = new Class[]{supportedAnnotationClass};
  }

  /**
   * Constructor for all Lombok-Processors
   *  @param supportedClass            kind of output elements this processor supports
   * @param supportedAnnotationClass  annotation this processor supports
   * @param equivalentAnnotationClass another equivalent annotation
   */
  @SuppressWarnings("unchecked")
  protected AbstractProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                              @NotNull Class<? extends Annotation> supportedAnnotationClass,
                              @NotNull Class<? extends Annotation> equivalentAnnotationClass) {
    this.configDiscovery = ConfigDiscovery.getInstance();
    this.supportedClass = supportedClass;
    this.supportedAnnotationClasses = new Class[]{supportedAnnotationClass, equivalentAnnotationClass};
  }

  /**
   * Constructor for all Lombok-Processors
   * @param supportedClass                  kind of output elements this processor supports
   * @param supportedAnnotationClass        annotation this processor supports
   * @param oneEquivalentAnnotationClass    another equivalent annotation
   * @param secondEquivalentAnnotationClass another equivalent annotation
   */
  @SuppressWarnings("unchecked")
  AbstractProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                    @NotNull Class<? extends Annotation> supportedAnnotationClass,
                    @NotNull Class<? extends Annotation> oneEquivalentAnnotationClass,
                    @NotNull Class<? extends Annotation> secondEquivalentAnnotationClass) {
    this.configDiscovery = ConfigDiscovery.getInstance();
    this.supportedClass = supportedClass;
    this.supportedAnnotationClasses = new Class[]{supportedAnnotationClass, oneEquivalentAnnotationClass, secondEquivalentAnnotationClass};
  }

  @NotNull
  public final Class<? extends Annotation>[] getSupportedAnnotationClasses() {
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
    definedMethods.removeIf(definedMethod -> PsiAnnotationSearchUtil.isAnnotatedWith(definedMethod, Tolerate.class));
  }

  protected boolean readAnnotationOrConfigProperty(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass,
                                                          @NotNull String annotationParameter, @NotNull ConfigKey configKey) {
    final boolean result;
    final Boolean declaredAnnotationValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(psiAnnotation, annotationParameter);
    if (null == declaredAnnotationValue) {
      result = configDiscovery.getBooleanLombokConfigProperty(configKey, psiClass);
    } else {
      result = declaredAnnotationValue;
    }
    return result;
  }

  protected static void addOnXAnnotations(@Nullable PsiAnnotation processedAnnotation,
                                          @NotNull PsiModifierList modifierList,
                                          @NotNull String onXParameterName) {
    if (processedAnnotation == null) {
      return;
    }

    Collection<String> annotationsToAdd = LombokProcessorUtil.getOnX(processedAnnotation, onXParameterName);
    for (String annotation : annotationsToAdd) {
      modifierList.addAnnotation(annotation);
    }
  }

  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.NONE;
  }

}
