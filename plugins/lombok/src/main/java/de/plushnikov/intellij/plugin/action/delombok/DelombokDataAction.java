package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.DataProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;

public class DelombokDataAction extends BaseDelombokAction {

  public DelombokDataAction() {
    super(new BaseDelombokHandler(
      new DataProcessor(new GetterProcessor(new GetterFieldProcessor()),
        new SetterProcessor(new SetterFieldProcessor()),
        new EqualsAndHashCodeProcessor(),
        new ToStringProcessor(),
        new RequiredArgsConstructorProcessor(),
        new NoArgsConstructorProcessor())));
  }
}
