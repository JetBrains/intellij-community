package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokConstructorAction extends AbstractDelombokAction {

  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ServiceManager.getService(AllArgsConstructorProcessor.class),
      ServiceManager.getService(NoArgsConstructorProcessor.class),
      ServiceManager.getService(RequiredArgsConstructorProcessor.class));
  }

}
