package de.plushnikov.intellij.plugin.processor.method;

public class BuilderExperimentalMethodProcessor extends BuilderMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalMethodProcessor() {
    super(lombok.experimental.Builder.class);
  }
}
