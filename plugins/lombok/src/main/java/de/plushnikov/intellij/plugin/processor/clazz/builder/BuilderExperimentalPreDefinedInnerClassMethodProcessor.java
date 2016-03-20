package de.plushnikov.intellij.plugin.processor.clazz.builder;

import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;

public class BuilderExperimentalPreDefinedInnerClassMethodProcessor extends BuilderPreDefinedInnerClassMethodProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalPreDefinedInnerClassMethodProcessor(@NotNull BuilderHandler builderHandler) {
    super(lombok.experimental.Builder.class, builderHandler);
  }
}
