package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.field.DelegateFieldProcessor;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokDelegateAction extends AbstractDelombokAction {

  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ServiceManager.getService(DelegateFieldProcessor.class), ServiceManager.getService(DelegateMethodProcessor.class));
  }
}
