package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;

public class DelombokEqualsAndHashCodeAction extends AbstractDelombokAction {

  @Override
  protected DelombokHandler createHandler() {
    return new DelombokHandler(LombokProcessorManager.getInstance().getEqualsAndHashCodeProcessor());
  }
}
