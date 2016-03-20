package de.plushnikov.intellij.plugin.processor.method;

import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;

public class BuilderExperimentalClassMethodProcessor extends BuilderClassMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalClassMethodProcessor(@NotNull BuilderHandler builderHandler) {
    super(lombok.experimental.Builder.class, builderHandler);
  }
}
