package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import lombok.extern.slf4j.XSlf4j;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class XSlf4jProcessor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "org.slf4j.ext.XLogger";
  private static final String LOGGER_CATEGORY = "%s.class";
  private static final String LOGGER_INITIALIZER = "org.slf4j.ext.XLoggerFactory.getXLogger(%s)";

  public XSlf4jProcessor(@NotNull ConfigDiscovery configDiscovery) {
    super(configDiscovery, XSlf4j.class, LOGGER_TYPE, LOGGER_INITIALIZER, LOGGER_CATEGORY);
  }
}
