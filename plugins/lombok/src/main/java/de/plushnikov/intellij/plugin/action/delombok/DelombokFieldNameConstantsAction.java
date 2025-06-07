package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;

public final class DelombokFieldNameConstantsAction extends AbstractDelombokAction {
  @Override
  protected @NotNull DelombokHandler createHandler() {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();
    return new DelombokHandler(true,
                               manager.getFieldNameConstantsOldProcessor(),
                               manager.getFieldNameConstantsFieldProcessor(),
                               manager.getFieldNameConstantsProcessor(),
                               manager.getFieldNameConstantsPredefinedInnerClassFieldProcessor());
  }
}
