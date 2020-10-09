package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.clazz.UtilityClassProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokUtilityClassAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true, ServiceManager.getService(UtilityClassProcessor.class));
  }
}
