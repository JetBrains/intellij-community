package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;

public final class DelombokBuilderAction extends AbstractDelombokAction {

  @Override
  protected @NotNull DelombokHandler createHandler() {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();
    return new DelombokHandler(true,
                               manager.getBuilderPreDefinedInnerClassFieldProcessor(),
                               manager.getBuilderPreDefinedInnerClassMethodProcessor(),
                               manager.getBuilderClassProcessor(),
                               manager.getBuilderClassMethodProcessor(),
                               manager.getBuilderMethodProcessor(),
                               manager.getBuilderProcessor());
  }
}
