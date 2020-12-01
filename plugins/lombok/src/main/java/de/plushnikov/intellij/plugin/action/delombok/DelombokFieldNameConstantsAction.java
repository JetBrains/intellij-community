package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsOldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsPredefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsProcessor;
import de.plushnikov.intellij.plugin.processor.field.FieldNameConstantsFieldProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokFieldNameConstantsAction extends AbstractDelombokAction {
  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true,
                               ApplicationManager.getApplication().getService(FieldNameConstantsOldProcessor.class),
                               ApplicationManager.getApplication().getService(FieldNameConstantsFieldProcessor.class),
                               ApplicationManager.getApplication().getService(FieldNameConstantsProcessor.class),
                               ApplicationManager.getApplication().getService(FieldNameConstantsPredefinedInnerClassFieldProcessor.class));
  }
}
