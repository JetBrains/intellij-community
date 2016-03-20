package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;

public class DelombokSetterAction extends BaseDelombokAction {
  public DelombokSetterAction() {
    super(new BaseDelombokHandler(new SetterProcessor(new SetterFieldProcessor()), new SetterFieldProcessor()));
  }
}