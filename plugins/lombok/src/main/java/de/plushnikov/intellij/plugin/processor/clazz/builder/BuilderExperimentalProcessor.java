package de.plushnikov.intellij.plugin.processor.clazz.builder;

import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;

public class BuilderExperimentalProcessor extends BuilderProcessor {
  @SuppressWarnings("deprecation")
  public BuilderExperimentalProcessor(@NotNull AllArgsConstructorProcessor allArgsConstructorProcessor, @NotNull BuilderHandler builderHandler) {
    super(lombok.experimental.Builder.class, allArgsConstructorProcessor, builderHandler);
  }
}
