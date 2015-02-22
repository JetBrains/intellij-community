package de.plushnikov.intellij.plugin.processor.clazz.builder;

import lombok.experimental.Builder;

public class BuilderExperimentalClassProcessor extends BuilderClassProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalClassProcessor() {
    super(Builder.class);
  }
}
