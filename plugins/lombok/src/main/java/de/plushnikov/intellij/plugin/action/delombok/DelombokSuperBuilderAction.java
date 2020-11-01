package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.builder.SuperBuilderClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.SuperBuilderPreDefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.SuperBuilderPreDefinedInnerClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.builder.SuperBuilderProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokSuperBuilderAction extends AbstractDelombokAction {

  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true,
                               ApplicationManager.getApplication().getService(SuperBuilderPreDefinedInnerClassFieldProcessor.class),
                               ApplicationManager.getApplication().getService(SuperBuilderPreDefinedInnerClassMethodProcessor.class),
                               ApplicationManager.getApplication().getService(SuperBuilderClassProcessor.class),
                               ApplicationManager.getApplication().getService(SuperBuilderProcessor.class));
  }
}
