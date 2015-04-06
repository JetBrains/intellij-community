package de.plushnikov.intellij.plugin.processor.clazz.builder;

public class BuilderExperimentalClassProcessor extends BuilderClassProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalClassProcessor() {
    super(lombok.experimental.Builder.class);
  }
}
