package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.StandardExceptionProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokStandardExceptionAction extends AbstractDelombokAction {
  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true, ApplicationManager.getApplication().getService(StandardExceptionProcessor.class));
  }
}
