package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;

public class DelombokDelegateAction extends AbstractDelombokAction {

  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();
    return new DelombokHandler(
      manager.getDelegateFieldProcessor(),
      manager.getDelegateMethodProcessor());
  }
}
