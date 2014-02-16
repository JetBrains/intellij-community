package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.field.DelegateFieldProcessor;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;

public class DelombokDelegateAction extends BaseDelombokAction {
  public DelombokDelegateAction() {
    super(new BaseDelombokHandler(new DelegateFieldProcessor(), new DelegateMethodProcessor()));
  }
}