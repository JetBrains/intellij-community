package de.plushnikov.intellij.plugin.processor.clazz.builder;

public class BuilderExperimentalPreDefinedInnerClassFieldProcessor extends BuilderPreDefinedInnerClassFieldProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalPreDefinedInnerClassFieldProcessor() {
    super(lombok.experimental.Builder.class);
  }
}
