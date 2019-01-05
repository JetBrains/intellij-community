package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import lombok.extern.flogger.Flogger;
import org.jetbrains.annotations.NotNull;

public class FloggerProcessor extends AbstractLogProcessor {
  private static final String LOGGER_TYPE = "com.google.common.flogger.FluentLogger";
  private static final String LOGGER_CATEGORY = "%s.class";
  private static final String LOGGER_INITIALIZER = "com.google.common.flogger.FluentLogger.forEnclosingClass()";

  public FloggerProcessor(@NotNull ConfigDiscovery configDiscovery) {
    super(configDiscovery, Flogger.class, LOGGER_TYPE, LOGGER_INITIALIZER, LOGGER_CATEGORY);
  }
}
