package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsPredefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsProcessor;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.util.ExtensionsUtil.findExtension;

public class DelombokFieldNameConstantsAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(true,
      findExtension(FieldNameConstantsProcessor.class),
      findExtension(FieldNameConstantsPredefinedInnerClassFieldProcessor.class));
  }
}
