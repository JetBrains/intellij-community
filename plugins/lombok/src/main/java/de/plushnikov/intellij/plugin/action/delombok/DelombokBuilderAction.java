package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalPreDefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalPreDefinedInnerClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderExperimentalProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.method.BuilderClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderExperimentalClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderExperimentalMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderMethodProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokBuilderAction extends BaseDelombokAction {

  public DelombokBuilderAction() {
    super(createHandler());
  }

  @NotNull
  private static BaseDelombokHandler createHandler() {
    final NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    final ToStringProcessor toStringProcessor = new ToStringProcessor();
    final BuilderHandler builderHandler = new BuilderHandler(toStringProcessor, noArgsConstructorProcessor);
    final AllArgsConstructorProcessor allArgsConstructorProcessor = new AllArgsConstructorProcessor();
    return new BaseDelombokHandler(true,
        new BuilderPreDefinedInnerClassFieldProcessor(builderHandler),
        new BuilderPreDefinedInnerClassMethodProcessor(builderHandler),
        new BuilderExperimentalPreDefinedInnerClassFieldProcessor(builderHandler),
        new BuilderExperimentalPreDefinedInnerClassMethodProcessor(builderHandler),
        new BuilderClassProcessor(builderHandler),
        new BuilderClassMethodProcessor(builderHandler),
        new BuilderMethodProcessor(builderHandler),
        new BuilderProcessor(allArgsConstructorProcessor, builderHandler),
        new BuilderExperimentalClassProcessor(builderHandler),
        new BuilderExperimentalClassMethodProcessor(builderHandler),
        new BuilderExperimentalMethodProcessor(builderHandler),
        new BuilderExperimentalProcessor(allArgsConstructorProcessor, builderHandler));
  }
}