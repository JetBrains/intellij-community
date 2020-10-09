package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokGetterAction extends AbstractDelombokAction {

  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ServiceManager.getService(GetterProcessor.class),
      ServiceManager.getService(GetterFieldProcessor.class));
  }
}
