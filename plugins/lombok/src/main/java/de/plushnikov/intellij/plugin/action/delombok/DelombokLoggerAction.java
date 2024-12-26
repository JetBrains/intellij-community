package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import org.jetbrains.annotations.NotNull;

public class DelombokLoggerAction extends AbstractDelombokAction {
  @Override
  protected @NotNull DelombokHandler createHandler() {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();
    return new DelombokHandler(
      manager.getCommonsLogProcessor(),
      manager.getJBossLogProcessor(),
      manager.getLog4jProcessor(),
      manager.getLog4j2Processor(),
      manager.getLogProcessor(),
      manager.getSlf4jProcessor(),
      manager.getXSlf4jProcessor(),
      manager.getFloggerProcessor(),
      manager.getCustomLogProcessor());
  }
}
