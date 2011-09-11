package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokFieldProcessor implements LombokFieldProcessor {

  private final String supportedAnnotation;
  private final Class supportedClass;

  protected AbstractLombokFieldProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    this.supportedAnnotation = supportedAnnotation;
    this.supportedClass = supportedClass;
  }

  public <Psi extends PsiElement> boolean acceptAnnotation(@Nullable String qualifiedName, @NotNull Class<Psi> type) {
    final String annotationName = StringUtil.notNullize(qualifiedName).trim();
    return (supportedAnnotation.equals(annotationName) || supportedAnnotation.endsWith(annotationName)) &&
        (type.isAssignableFrom(supportedClass));
  }
}
