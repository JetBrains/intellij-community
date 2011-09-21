package de.plushnikov.intellij.lombok.processor.clazz.log;

import lombok.extern.log4j.Log4j;

/**
 * @author Plushnikov Michail
 */
public class Log4jProcessor extends AbstractLogProcessor {

  private static final String CLASS_NAME = Log4j.class.getName();
  private static final String LOGGER_TYPE = "org.apache.log4j.Logger";
  private static final String LOGGER_INITIALIZER = "org.apache.log4j.Logger.getLogger(%s.class);";

  public Log4jProcessor() {
    super(CLASS_NAME, LOGGER_TYPE, LOGGER_INITIALIZER);
  }
}
