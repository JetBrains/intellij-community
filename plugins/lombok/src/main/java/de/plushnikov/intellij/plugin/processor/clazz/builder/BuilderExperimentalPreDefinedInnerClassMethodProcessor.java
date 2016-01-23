package de.plushnikov.intellij.plugin.processor.clazz.builder;

public class BuilderExperimentalPreDefinedInnerClassMethodProcessor extends BuilderPreDefinedInnerClassMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalPreDefinedInnerClassMethodProcessor() {
    super(lombok.experimental.Builder.class);
  }
}
