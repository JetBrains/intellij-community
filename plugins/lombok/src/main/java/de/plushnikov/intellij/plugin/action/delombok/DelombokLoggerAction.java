package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.log.CommonsLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.FloggerProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.JBossLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4j2Processor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.LogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.XSlf4jProcessor;

public class DelombokLoggerAction extends BaseDelombokAction {
  public DelombokLoggerAction() {
    super(new BaseDelombokHandler(new CommonsLogProcessor(), new JBossLogProcessor(),
      new Log4jProcessor(), new Log4j2Processor(), new LogProcessor(),
      new Slf4jProcessor(), new XSlf4jProcessor(), new FloggerProcessor()));
  }
}
