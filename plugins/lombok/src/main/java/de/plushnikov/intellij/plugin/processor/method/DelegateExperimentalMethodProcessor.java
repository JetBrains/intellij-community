package de.plushnikov.intellij.plugin.processor.method;

import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

public class DelegateExperimentalMethodProcessor extends DelegateMethodProcessor {

  public DelegateExperimentalMethodProcessor(@NotNull DelegateHandler delegateHandler) {
    super(Delegate.class, delegateHandler);
  }
}
