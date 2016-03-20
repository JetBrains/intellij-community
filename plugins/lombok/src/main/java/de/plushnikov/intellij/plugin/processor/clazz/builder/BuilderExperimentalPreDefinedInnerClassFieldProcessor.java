package de.plushnikov.intellij.plugin.processor.clazz.builder;

import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;

public class BuilderExperimentalPreDefinedInnerClassFieldProcessor extends BuilderPreDefinedInnerClassFieldProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalPreDefinedInnerClassFieldProcessor(@NotNull BuilderHandler builderHandler) {
    super(lombok.experimental.Builder.class, builderHandler);
  }
}
