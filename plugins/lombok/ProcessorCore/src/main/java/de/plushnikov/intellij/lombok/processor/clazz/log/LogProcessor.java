package de.plushnikov.intellij.lombok.processor.clazz.log;

import lombok.extern.java.Log;

/**
 * @author Plushnikov Michail
 */
public class LogProcessor extends AbstractLogProcessor {
  private static final String CLASS_NAME = Log.class.getName();

  private static final String LOGGER_TYPE = "java.util.logging.Logger";
  private static final String LOGGER_INITIALIZER = "java.util.logging.Logger.getLogger(LogExample.class.getName());";

  public LogProcessor() {
    super(CLASS_NAME, LOGGER_TYPE, LOGGER_INITIALIZER);
  }
}
