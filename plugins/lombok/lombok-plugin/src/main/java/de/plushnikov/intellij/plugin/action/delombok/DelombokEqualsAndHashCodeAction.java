package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor;

public class DelombokEqualsAndHashCodeAction extends BaseDelombokAction {
  public DelombokEqualsAndHashCodeAction() {
    super(new BaseDelombokHandler(new EqualsAndHashCodeProcessor()));
  }
}