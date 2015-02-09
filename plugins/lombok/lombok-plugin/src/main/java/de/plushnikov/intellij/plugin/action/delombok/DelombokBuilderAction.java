package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.BuilderClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.BuilderExperimentalClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.BuilderExperimentalProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.BuilderProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderExperimentalClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderExperimentalMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderMethodProcessor;

public class DelombokBuilderAction extends BaseDelombokAction {

  public DelombokBuilderAction() {
    super(new BaseDelombokHandler(
        new BuilderClassProcessor(), new BuilderClassMethodProcessor(),
        new BuilderMethodProcessor(), new BuilderProcessor(),
        new BuilderExperimentalClassProcessor(), new BuilderExperimentalClassMethodProcessor(),
        new BuilderExperimentalMethodProcessor(), new BuilderExperimentalProcessor()));
  }
}