package de.plushnikov.intellij.plugin.processor.clazz.log;

import lombok.extern.java.Log;

/**
 * @author Plushnikov Michail
 */
public class LogProcessor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "java.util.logging.Logger";
  private static final String LOGGER_CATEGORY = "%s.class.getName()";
  private static final String LOGGER_INITIALIZER = "java.util.logging.Logger.getLogger(%s)";

  public LogProcessor() {
    super(Log.class, LOGGER_TYPE, LOGGER_INITIALIZER, LOGGER_CATEGORY);
  }
}
