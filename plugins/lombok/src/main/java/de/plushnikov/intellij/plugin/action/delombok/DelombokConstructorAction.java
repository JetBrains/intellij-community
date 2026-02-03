package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;

public final class DelombokConstructorAction extends AbstractDelombokAction {

  @Override
  protected @NotNull DelombokHandler createHandler() {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();
    return new DelombokHandler(
      manager.getAllArgsConstructorProcessor(),
      manager.getNoArgsConstructorProcessor(),
      manager.getRequiredArgsConstructorProcessor());
  }
}
