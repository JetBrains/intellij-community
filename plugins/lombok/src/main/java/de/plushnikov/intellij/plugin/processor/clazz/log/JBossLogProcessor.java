package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.LombokNames;

/**
 * @author Plushnikov Michail
 */
public class JBossLogProcessor extends AbstractTopicSupportingSimpleLogProcessor {

  private static final String LOGGER_TYPE = "org.jboss.logging.Logger";
  private static final String LOGGER_INITIALIZER = "org.jboss.logging.Logger.getLogger(%s)";

  public JBossLogProcessor() {
    super(LombokNames.JBOSS_LOG, LOGGER_TYPE, LOGGER_INITIALIZER, LoggerInitializerParameter.TYPE);
  }
}
