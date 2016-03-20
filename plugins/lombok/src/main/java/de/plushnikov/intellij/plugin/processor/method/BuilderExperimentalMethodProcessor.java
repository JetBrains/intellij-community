package de.plushnikov.intellij.plugin.processor.method;

import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;

public class BuilderExperimentalMethodProcessor extends BuilderMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalMethodProcessor(@NotNull BuilderHandler builderHandler) {
    super(lombok.experimental.Builder.class, builderHandler);
  }
}
