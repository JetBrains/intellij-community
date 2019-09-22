package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.clazz.DataProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokDataAction extends AbstractDelombokAction {

  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(ServiceManager.getService(DataProcessor.class));
  }
}
