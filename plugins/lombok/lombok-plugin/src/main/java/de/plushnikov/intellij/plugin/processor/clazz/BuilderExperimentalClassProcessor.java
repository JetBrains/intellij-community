package de.plushnikov.intellij.plugin.processor.clazz;

import lombok.experimental.Builder;

public class BuilderExperimentalClassProcessor extends BuilderClassProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalClassProcessor() {
    super(Builder.class);
  }
}
