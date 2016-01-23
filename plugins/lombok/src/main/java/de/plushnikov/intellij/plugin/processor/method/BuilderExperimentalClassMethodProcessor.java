package de.plushnikov.intellij.plugin.processor.method;

public class BuilderExperimentalClassMethodProcessor extends BuilderClassMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalClassMethodProcessor() {
    super(lombok.experimental.Builder.class);
  }
}
