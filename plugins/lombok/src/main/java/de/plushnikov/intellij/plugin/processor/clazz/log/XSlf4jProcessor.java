package de.plushnikov.intellij.plugin.processor.clazz.log;

import lombok.extern.slf4j.XSlf4j;

/**
 * @author Plushnikov Michail
 */
public class XSlf4jProcessor extends AbstractTopicSupportingSimpleLogProcessor {

  private static final String LOGGER_TYPE = "org.slf4j.ext.XLogger";
  private static final String LOGGER_INITIALIZER = "org.slf4j.ext.XLoggerFactory.getXLogger(%s)";

  public XSlf4jProcessor() {
    super(XSlf4j.class, LOGGER_TYPE, LOGGER_INITIALIZER, LoggerInitializerParameter.TYPE);
  }
}
