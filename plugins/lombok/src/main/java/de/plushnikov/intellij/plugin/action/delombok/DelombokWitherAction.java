package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.clazz.WitherProcessor;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokWitherAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ServiceManager.getService(WitherProcessor.class),
      ServiceManager.getService(WitherFieldProcessor.class));
  }
}
