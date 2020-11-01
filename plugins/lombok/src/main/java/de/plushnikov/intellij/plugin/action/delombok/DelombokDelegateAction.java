package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.field.DelegateFieldProcessor;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokDelegateAction extends AbstractDelombokAction {

  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ApplicationManager.getApplication().getService(DelegateFieldProcessor.class),
      ApplicationManager.getApplication().getService(DelegateMethodProcessor.class));
  }
}
