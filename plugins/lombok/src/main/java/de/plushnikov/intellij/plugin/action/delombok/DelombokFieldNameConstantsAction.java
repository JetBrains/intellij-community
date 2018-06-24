package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.FieldNameConstantsProcessor;
import de.plushnikov.intellij.plugin.processor.field.FieldNameConstantsFieldProcessor;

public class DelombokFieldNameConstantsAction extends BaseDelombokAction {
  public DelombokFieldNameConstantsAction() {
    super(new BaseDelombokHandler(new FieldNameConstantsProcessor(new FieldNameConstantsFieldProcessor()), new FieldNameConstantsFieldProcessor()));
  }
}
