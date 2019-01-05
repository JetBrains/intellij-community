package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import lombok.extern.jbosslog.JBossLog;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class JBossLogProcessor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "org.jboss.logging.Logger";
  private static final String LOGGER_CATEGORY = "%s.class";
  private static final String LOGGER_INITIALIZER = "org.jboss.logging.Logger.getLogger(%s)";

  public JBossLogProcessor(@NotNull ConfigDiscovery configDiscovery) {
    super(configDiscovery, JBossLog.class, LOGGER_TYPE, LOGGER_INITIALIZER, LOGGER_CATEGORY);
  }
}
