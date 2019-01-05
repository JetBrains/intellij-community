package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.WitherProcessor;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.util.ExtensionsUtil.findExtension;

public class DelombokWitherAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      findExtension(WitherProcessor.class),
      findExtension(WitherFieldProcessor.class));
  }
}
