package de.plushnikov.intellij.plugin.processor.method;

import lombok.experimental.Builder;

public class BuilderExperimentalMethodProcessor extends BuilderMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalMethodProcessor() {
    super(Builder.class);
  }
}
