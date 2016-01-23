package de.plushnikov.intellij.plugin.processor.field;

import lombok.experimental.Delegate;

/**
 * Inspect and validate @Delegate lombok annotation on a field
 * Creates delegation methods for this field
 *
 * @author Plushnikov Michail
 */
public class DelegateExperimentalFieldProcessor extends DelegateFieldProcessor {

  public DelegateExperimentalFieldProcessor() {
    super(Delegate.class);
  }
}
