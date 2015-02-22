package de.plushnikov.intellij.plugin.processor.clazz.builder;

import lombok.experimental.Builder;

public class BuilderExperimentalPreDefinedInnerClassMethodProcessor extends BuilderPreDefinedInnerClassMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalPreDefinedInnerClassMethodProcessor() {
    super(Builder.class);
  }
}
