package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;

public class DelombokToStringAction extends BaseDelombokAction {
  public DelombokToStringAction() {
    super(new BaseDelombokHandler(new ToStringProcessor()));
  }
}