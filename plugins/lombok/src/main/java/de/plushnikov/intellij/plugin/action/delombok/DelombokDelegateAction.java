package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.field.DelegateFieldProcessor;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.util.ExtensionsUtil.findExtension;

public class DelombokDelegateAction extends AbstractDelombokAction {

  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      findExtension(DelegateFieldProcessor.class), findExtension(DelegateMethodProcessor.class));
  }
}
