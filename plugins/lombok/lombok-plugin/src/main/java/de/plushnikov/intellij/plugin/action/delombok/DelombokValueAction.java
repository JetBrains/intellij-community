package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.ValueExperimentalProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ValueProcessor;

public class DelombokValueAction extends BaseDelombokAction {
  public DelombokValueAction() {
    super(new BaseDelombokHandler(new ValueProcessor(), new ValueExperimentalProcessor()));
  }
}