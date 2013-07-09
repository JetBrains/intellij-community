package de.plushnikov.intellij.lombok.processor.clazz.log;

import lombok.extern.slf4j.XSlf4j;

/**
 * @author Plushnikov Michail
 */
public class XSlf4jProcessor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "org.slf4j.ext.XLogger";
  private static final String LOGGER_INITIALIZER = "org.slf4j.ext.XLoggerFactory.getXLogger(%s.class)";

  public XSlf4jProcessor() {
    super(XSlf4j.class, LOGGER_TYPE, LOGGER_INITIALIZER);
  }
}
