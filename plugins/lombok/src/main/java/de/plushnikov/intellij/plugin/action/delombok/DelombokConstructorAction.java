package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.util.ExtensionsUtil.findExtension;

public class DelombokConstructorAction extends AbstractDelombokAction {

  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      findExtension(AllArgsConstructorProcessor.class),
      findExtension(NoArgsConstructorProcessor.class),
      findExtension(RequiredArgsConstructorProcessor.class));
  }

}
