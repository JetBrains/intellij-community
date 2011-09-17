package de.plushnikov.intellij.lombok.processor.clazz;


import de.plushnikov.intellij.lombok.processor.AbstractLombokProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Base lombok processor class for class annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokClassProcessor extends AbstractLombokProcessor implements LombokClassProcessor {

  protected AbstractLombokClassProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    super(supportedAnnotation, supportedClass);
  }

}
