package de.plushnikov.intellij.plugin.processor.clazz.builder;

import lombok.experimental.Builder;

public class BuilderExperimentalProcessor extends BuilderProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalProcessor() {
    super(Builder.class);
  }
}
