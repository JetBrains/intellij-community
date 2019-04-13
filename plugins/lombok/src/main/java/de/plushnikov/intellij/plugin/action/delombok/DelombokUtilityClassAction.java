package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.UtilityClassProcessor;
import de.plushnikov.intellij.plugin.util.ExtensionsUtil;
import org.jetbrains.annotations.NotNull;

public class DelombokUtilityClassAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true, ExtensionsUtil.findExtension(UtilityClassProcessor.class));
  }
}
