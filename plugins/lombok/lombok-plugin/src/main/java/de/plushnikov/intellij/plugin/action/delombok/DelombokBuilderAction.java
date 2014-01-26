package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.BuilderProcessor;

public class DelombokBuilderAction extends BaseDelombokAction {
  public DelombokBuilderAction() {
    super(new BaseDelombokHandler(new BuilderProcessor()));
  }
}