package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;

public final class DelombokUtilityClassAction extends AbstractDelombokAction {
  @Override
  protected @NotNull DelombokHandler createHandler() {
    return new DelombokHandler(true, LombokProcessorManager.getInstance().getUtilityClassProcessor());
  }
}
