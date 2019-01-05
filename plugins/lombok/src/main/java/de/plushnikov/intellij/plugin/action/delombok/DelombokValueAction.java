package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.ValueProcessor;
import de.plushnikov.intellij.plugin.util.ExtensionsUtil;
import org.jetbrains.annotations.NotNull;

public class DelombokValueAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(ExtensionsUtil.findExtension(ValueProcessor.class));
  }
}
