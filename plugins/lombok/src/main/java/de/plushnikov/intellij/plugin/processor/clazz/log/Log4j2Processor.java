package de.plushnikov.intellij.plugin.processor.clazz.log;

import lombok.extern.log4j.Log4j2;

/**
 * @author Plushnikov Michail
 */
public class Log4j2Processor extends AbstractTopicSupportingSimpleLogProcessor {

  private static final String LOGGER_TYPE = "org.apache.logging.log4j.Logger";
  private static final String LOGGER_INITIALIZER = "org.apache.logging.log4j.LogManager.getLogger(%s)";

  public Log4j2Processor() {
    super(Log4j2.class, LOGGER_TYPE, LOGGER_INITIALIZER, LoggerInitializerParameter.TYPE);
  }
}
