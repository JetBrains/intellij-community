package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.DataProcessor;

public class DelombokDataAction extends BaseDelombokAction {

  public DelombokDataAction() {
    super(new BaseDelombokHandler(new DataProcessor()));
  }
}