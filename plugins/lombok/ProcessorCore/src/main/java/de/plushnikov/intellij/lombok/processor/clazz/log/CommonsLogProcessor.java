package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.psi.PsiField;
import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Plushnikov Michail
 */
public class CommonsLogProcessor extends AbstractLogProcessor {

  private static final String LOGGER_DEFINITION = "private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(LogExample.class);";

  private static final String CLASS_NAME = CommonsLog.class.getName();

  public CommonsLogProcessor() {
    super(CLASS_NAME, PsiField.class, LOGGER_DEFINITION);
  }
}
