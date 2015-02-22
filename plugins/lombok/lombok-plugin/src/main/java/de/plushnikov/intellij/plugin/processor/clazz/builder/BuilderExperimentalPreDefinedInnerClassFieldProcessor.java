package de.plushnikov.intellij.plugin.processor.clazz.builder;

import lombok.experimental.Builder;

public class BuilderExperimentalPreDefinedInnerClassFieldProcessor extends BuilderPreDefinedInnerClassFieldProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalPreDefinedInnerClassFieldProcessor() {
    super(Builder.class);
  }
}
