package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;

public class DelombokGetterAction extends BaseDelombokAction {
  public DelombokGetterAction() {
    super(new BaseDelombokHandler(new GetterProcessor(new GetterFieldProcessor()), new GetterFieldProcessor()));
  }
}