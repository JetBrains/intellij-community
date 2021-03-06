package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.UtilityClassProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokUtilityClassAction extends AbstractDelombokAction {
  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true, ApplicationManager.getApplication().getService(UtilityClassProcessor.class));
  }
}
