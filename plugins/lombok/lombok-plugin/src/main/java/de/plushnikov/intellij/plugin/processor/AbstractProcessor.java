package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKeys;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.Tolerate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Base lombok processor class
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractProcessor implements Processor {
  /**
   * Anntotation qualified name this processor supports
   */
  private final String supportedAnnotation;
  /**
   * Anntotation class this processor supports
   */
  private final Class<? extends Annotation> supportedAnnotationClass;
  /**
   * Kind of output elements this processor supports
   */
  private final Class<? extends PsiElement> supportedClass;

  /**
   * Constructor for all Lombok-Processors
   *
   * @param supportedAnnotationClass annotation this processor supports
   * @param supportedClass           kind of output elements this processor supports
   */
  protected AbstractProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
    this.supportedAnnotationClass = supportedAnnotationClass;
    this.supportedAnnotation = supportedAnnotationClass.getName();
    this.supportedClass = supportedClass;
  }

  @NotNull
  @Override
  public final String getSupportedAnnotation() {
    return supportedAnnotation;
  }

  @NotNull
  @Override
  public final Class<? extends Annotation> getSupportedAnnotationClass() {
    return supportedAnnotationClass;
  }

  @NotNull
  @Override
  public final Class<? extends PsiElement> getSupportedClass() {
    return supportedClass;
  }

  public boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<? extends PsiElement> type) {
    final String annotationName = StringUtil.notNullize(psiAnnotation.getQualifiedName()).trim();
    return supportedAnnotation.equals(annotationName) && canProduce(type);
  }

  @Override
  public boolean isEnabled(@NotNull Project project) {
    return true;//TODO make it configurable
  }

  @Override
  public boolean canProduce(@NotNull Class<? extends PsiElement> type) {
    return type.isAssignableFrom(supportedClass);
  }

  @NotNull
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    return Collections.emptyList();
  }

  @NotNull
  public abstract Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass);

  protected String getGetterName(final @NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);

    final String psiFieldName = psiField.getName();
    final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());

    return LombokUtils.toGetterName(accessorsInfo, psiFieldName, isBoolean);
  }

  protected void filterToleratedElements(@NotNull Collection<? extends PsiModifierListOwner> definedMethods) {
    final Iterator<? extends PsiModifierListOwner> methodIterator = definedMethods.iterator();
    while (methodIterator.hasNext()) {
      PsiModifierListOwner definedMethod = methodIterator.next();
      if (PsiAnnotationUtil.isAnnotatedWith(definedMethod, Tolerate.class)) {
        methodIterator.remove();
      }
    }
  }

  public static boolean readAnnotationOrConfigProperty(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass,
                                                       @NotNull String annotationParameter, @NotNull ConfigKeys configKeys) {
    final boolean result;
    final Boolean declaredAnnotationValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(psiAnnotation, annotationParameter);
    if (null == declaredAnnotationValue) {
      result = ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(configKeys, psiClass);
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
}
