package de.plushnikov.intellij.plugin.processor.field;

import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

/**
 * Inspect and validate @Delegate lombok annotation on a field
 * Creates delegation methods for this field
 *
 * @author Plushnikov Michail
 */
public class DelegateExperimentalFieldProcessor extends DelegateFieldProcessor {

  public DelegateExperimentalFieldProcessor(@NotNull DelegateHandler delegateHandler) {
    super(Delegate.class, delegateHandler);
  }
}
