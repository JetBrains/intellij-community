package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.log.CommonsLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.FloggerProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.JBossLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4j2Processor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.LogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.XSlf4jProcessor;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.util.ExtensionsUtil.findExtension;

public class DelombokLoggerAction extends AbstractDelombokAction {
  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(
      findExtension(CommonsLogProcessor.class), findExtension(JBossLogProcessor.class),
      findExtension(Log4jProcessor.class), findExtension(Log4j2Processor.class), findExtension(LogProcessor.class),
      findExtension(Slf4jProcessor.class), findExtension(XSlf4jProcessor.class), findExtension(FloggerProcessor.class));
  }
}
