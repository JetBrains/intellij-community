package de.plushnikov.intellij.plugin.processor.method;

import lombok.experimental.Delegate;

public class DelegateExperimentalMethodProcessor extends DelegateMethodProcessor {

  public DelegateExperimentalMethodProcessor() {
    super(Delegate.class);
  }
}
