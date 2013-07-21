package de.plushnikov.intellij.lombok.processor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Base lombok processor class
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokProcessor implements LombokProcessor {
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
  private final Class<?> supportedClass;

  /**
   * Constructor for all Lombok-Processors
   *
   * @param supportedAnnotationClass anntotation this processor supports
   * @param supportedClass           kind of output elements this processor supports
   */
  protected AbstractLombokProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    this.supportedAnnotationClass = supportedAnnotationClass;
    this.supportedAnnotation = supportedAnnotationClass.getName();
    this.supportedClass = supportedClass;
  }

  @NotNull
  @Override
  public String getSupportedAnnotation() {
    return supportedAnnotation;
  }

  @NotNull
  @Override
  public Class<? extends Annotation> getSupportedAnnotationClass() {
    return supportedAnnotationClass;
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
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    return Collections.emptyList();
  }
}
