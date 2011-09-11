package de.plushnikov.intellij.lombok.processor.clazz.log;

import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Plushnikov Michail
 */
public class CommonsLogProcessor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "org.apache.commons.logging.Log";
  private static final String LOGGER_INITIALIZER = "org.apache.commons.logging.LogFactory.getLog(LogExample.class);";

  private static final String CLASS_NAME = CommonsLog.class.getName();

  public CommonsLogProcessor() {
    super(CLASS_NAME, LOGGER_TYPE, LOGGER_INITIALIZER);
  }
}
