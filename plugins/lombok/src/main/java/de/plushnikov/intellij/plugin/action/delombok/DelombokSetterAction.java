package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokSetterAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ServiceManager.getService(SetterProcessor.class),
      ServiceManager.getService(SetterFieldProcessor.class));
  }
}
