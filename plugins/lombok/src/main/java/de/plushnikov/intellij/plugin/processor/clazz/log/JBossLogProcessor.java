package de.plushnikov.intellij.plugin.processor.clazz.log;

import com.intellij.openapi.components.Service;
import de.plushnikov.intellij.plugin.LombokClassNames;

/**
 * @author Plushnikov Michail
 */
@Service
public final class JBossLogProcessor extends AbstractTopicSupportingSimpleLogProcessor {

  private static final String LOGGER_TYPE = "org.jboss.logging.Logger";
  private static final String LOGGER_INITIALIZER = "org.jboss.logging.Logger.getLogger(%s)";

  public JBossLogProcessor() {
    super(LombokClassNames.JBOSS_LOG, LOGGER_TYPE, LOGGER_INITIALIZER, LoggerInitializerParameter.TYPE);
  }
}
