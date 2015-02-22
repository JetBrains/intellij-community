package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalPreDefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalPreDefinedInnerClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderExperimentalClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderExperimentalMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderMethodProcessor;

public class DelombokBuilderAction extends BaseDelombokAction {

  public DelombokBuilderAction() {
    super(new BaseDelombokHandler(true,
        new BuilderPreDefinedInnerClassFieldProcessor(), new BuilderPreDefinedInnerClassMethodProcessor(),
        new BuilderExperimentalPreDefinedInnerClassFieldProcessor(), new BuilderExperimentalPreDefinedInnerClassMethodProcessor(),
        new BuilderClassProcessor(), new BuilderClassMethodProcessor(),
        new BuilderMethodProcessor(), new BuilderProcessor(),
        new BuilderExperimentalClassProcessor(), new BuilderExperimentalClassMethodProcessor(),
        new BuilderExperimentalMethodProcessor(), new BuilderExperimentalProcessor()));
  }
}