package de.plushnikov.intellij.plugin.processor.clazz.builder;

public class BuilderExperimentalProcessor extends BuilderProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalProcessor() {
    super(lombok.experimental.Builder.class);
  }
}
