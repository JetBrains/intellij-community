package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.processor.clazz.log.*;
import org.jetbrains.annotations.NotNull;

public class DelombokLoggerAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ServiceManager.getService(CommonsLogProcessor.class), ServiceManager.getService(JBossLogProcessor.class),
      ServiceManager.getService(Log4jProcessor.class), ServiceManager.getService(Log4j2Processor.class), ServiceManager.getService(LogProcessor.class),
      ServiceManager.getService(Slf4jProcessor.class), ServiceManager.getService(XSlf4jProcessor.class), ServiceManager.getService(FloggerProcessor.class),
      ServiceManager.getService(CustomLogProcessor.class));
  }
}
