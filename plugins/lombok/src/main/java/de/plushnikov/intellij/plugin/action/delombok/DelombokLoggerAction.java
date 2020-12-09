package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.application.ApplicationManager;
import de.plushnikov.intellij.plugin.processor.clazz.log.*;
import org.jetbrains.annotations.NotNull;

public class DelombokLoggerAction extends AbstractDelombokAction {
  @Override
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      ApplicationManager.getApplication().getService(CommonsLogProcessor.class),
      ApplicationManager.getApplication().getService(JBossLogProcessor.class),
      ApplicationManager.getApplication().getService(Log4jProcessor.class),
      ApplicationManager.getApplication().getService(Log4j2Processor.class),
      ApplicationManager.getApplication().getService(LogProcessor.class),
      ApplicationManager.getApplication().getService(Slf4jProcessor.class),
      ApplicationManager.getApplication().getService(XSlf4jProcessor.class),
      ApplicationManager.getApplication().getService(FloggerProcessor.class),
      ApplicationManager.getApplication().getService(CustomLogProcessor.class));
  }
}
