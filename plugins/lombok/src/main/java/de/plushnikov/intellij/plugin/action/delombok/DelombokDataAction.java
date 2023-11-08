package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;

public final class DelombokDataAction extends AbstractDelombokAction {

  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(LombokProcessorManager.getInstance().getDataProcessor());
  }
}
