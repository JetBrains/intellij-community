package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.LombokNames;

/**
 * @author Plushnikov Michail
 */
public class LogProcessor extends AbstractTopicSupportingSimpleLogProcessor {

  private static final String LOGGER_TYPE = "java.util.logging.Logger";
  private static final String LOGGER_INITIALIZER = "java.util.logging.Logger.getLogger(%s)";

  public LogProcessor() {
    super(LombokNames.JAVA_LOG, LOGGER_TYPE, LOGGER_INITIALIZER, LoggerInitializerParameter.NAME);
  }
}
