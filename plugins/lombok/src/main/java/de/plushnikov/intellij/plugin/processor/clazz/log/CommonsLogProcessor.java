package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import lombok.extern.apachecommons.CommonsLog;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class CommonsLogProcessor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "org.apache.commons.logging.Log";
  private static final String LOGGER_CATEGORY = "%s.class";
  private static final String LOGGER_INITIALIZER = "org.apache.commons.logging.LogFactory.getLog(%s)";

  public CommonsLogProcessor(@NotNull ConfigDiscovery configDiscovery) {
    super(configDiscovery, CommonsLog.class, LOGGER_TYPE, LOGGER_INITIALIZER, LOGGER_CATEGORY);
  }
}
