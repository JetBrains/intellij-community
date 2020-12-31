package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderPreDefinedInnerClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.BuilderProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderMethodProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokBuilderAction extends AbstractDelombokAction {

  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true,
                               ApplicationManager.getApplication().getService(BuilderPreDefinedInnerClassFieldProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderPreDefinedInnerClassMethodProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderClassProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderClassMethodProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderMethodProcessor.class),
                               ApplicationManager.getApplication().getService(BuilderProcessor.class));
  }
}
