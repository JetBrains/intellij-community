package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.log.*;

public class DelombokLoggerAction extends BaseDelombokAction {
  public DelombokLoggerAction() {
    super(new BaseDelombokHandler(new CommonsLogProcessor(), new JBossLogProcessor(),
      new Log4jProcessor(), new Log4j2Processor(), new LogProcessor(),
      new Slf4jProcessor(), new XSlf4jProcessor(), new FloggerProcessor()));
  }
}
