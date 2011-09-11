package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.psi.PsiField;
import lombok.extern.log4j.Log4j;

/**
 * @author Plushnikov Michail
 */
public class Log4jProcessor extends AbstractLogProcessor {

  private static final String CLASS_NAME = Log4j.class.getName();
  private static final String LOGGER_DEFINITION = "private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LogExample.class);";

  public Log4jProcessor() {
    super(CLASS_NAME, PsiField.class, LOGGER_DEFINITION);
  }
}
