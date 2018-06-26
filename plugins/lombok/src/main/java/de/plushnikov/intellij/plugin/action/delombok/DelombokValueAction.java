package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ValueProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;

public class DelombokValueAction extends BaseDelombokAction {
  public DelombokValueAction() {
    super(new BaseDelombokHandler(
      new ValueProcessor(
        new GetterProcessor(new GetterFieldProcessor()),
        new EqualsAndHashCodeProcessor(),
        new ToStringProcessor(),
        new AllArgsConstructorProcessor(), new NoArgsConstructorProcessor())));
  }
}
