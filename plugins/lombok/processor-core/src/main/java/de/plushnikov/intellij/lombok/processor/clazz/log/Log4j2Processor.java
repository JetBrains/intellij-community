package de.plushnikov.intellij.lombok.processor.clazz.log;

import lombok.extern.log4j.Log4j2;

/**
 * @author Plushnikov Michail
 */
public class Log4j2Processor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "org.apache.logging.log4j.Logger";
  private static final String LOGGER_INITIALIZER = "org.apache.logging.log4j.LogManager.getLogger(%s.class)";

  public Log4j2Processor() {
    super(Log4j2.class, LOGGER_TYPE, LOGGER_INITIALIZER);
  }
}
