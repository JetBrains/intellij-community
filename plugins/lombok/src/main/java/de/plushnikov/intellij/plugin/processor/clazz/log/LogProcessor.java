package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LogProcessor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "java.util.logging.Logger";
  private static final String LOGGER_CATEGORY = "%s.class.getName()";
  private static final String LOGGER_INITIALIZER = "java.util.logging.Logger.getLogger(%s)";

  public LogProcessor(@NotNull ConfigDiscovery configDiscovery) {
    super(configDiscovery, Log.class, LOGGER_TYPE, LOGGER_INITIALIZER, LOGGER_CATEGORY);
  }
}
