package de.plushnikov.intellij.plugin.processor.method;

import lombok.experimental.Builder;

public class BuilderExperimentalClassMethodProcessor extends BuilderClassMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalClassMethodProcessor() {
    super(Builder.class);
  }
}
