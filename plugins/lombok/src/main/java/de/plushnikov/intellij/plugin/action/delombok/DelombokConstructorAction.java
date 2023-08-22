package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokConstructorAction extends AbstractDelombokAction {

  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ApplicationManager.getApplication().getService(AllArgsConstructorProcessor.class),
      ApplicationManager.getApplication().getService(NoArgsConstructorProcessor.class),
      ApplicationManager.getApplication().getService(RequiredArgsConstructorProcessor.class));
  }

}
