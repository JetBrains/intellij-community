package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor;

import static de.plushnikov.intellij.plugin.util.ExtensionsUtil.findExtension;

public class DelombokEqualsAndHashCodeAction extends AbstractDelombokAction {

  @Override
  protected DelombokHandler createHandler() {
    return new DelombokHandler(findExtension(EqualsAndHashCodeProcessor.class));
  }
}
