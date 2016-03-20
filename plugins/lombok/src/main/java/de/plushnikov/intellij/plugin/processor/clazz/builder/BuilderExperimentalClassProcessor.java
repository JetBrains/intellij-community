package de.plushnikov.intellij.plugin.processor.clazz.builder;

import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;

public class BuilderExperimentalClassProcessor extends BuilderClassProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalClassProcessor(@NotNull BuilderHandler builderHandler) {
    super(lombok.experimental.Builder.class, builderHandler);
  }
}
