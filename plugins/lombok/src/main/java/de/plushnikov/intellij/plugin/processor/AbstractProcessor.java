package de.plushnikov.intellij.plugin.processor;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

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
                              @NotNull String @NotNull ... supportedAnnotationClasses) {
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

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.NONE;
  }
}
