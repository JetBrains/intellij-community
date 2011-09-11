package de.plushnikov.intellij.lombok.processor.clazz;


import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokClassProcessor implements LombokClassProcessor {

  private final String supportedAnnotation;
  private final Class supportedClass;

  protected AbstractLombokClassProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    this.supportedAnnotation = supportedAnnotation;
    this.supportedClass = supportedClass;
  }

  public boolean acceptAnnotation(@Nullable String qualifiedName, @NotNull Class type) {
    final String annotationName = StringUtil.notNullize(qualifiedName).trim();
    return (supportedAnnotation.equals(annotationName) || supportedAnnotation.endsWith(annotationName))
        && type.isAssignableFrom(supportedClass);

  }
}
