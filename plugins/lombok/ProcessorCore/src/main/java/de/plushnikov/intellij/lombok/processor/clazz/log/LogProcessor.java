package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.psi.PsiField;
import lombok.extern.java.Log;

/**
 * @author Plushnikov Michail
 */
public class LogProcessor extends AbstractLogProcessor {
  private static final String CLASS_NAME = Log.class.getName();
  private static final String LOGGER_DEFINITION = "private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LogExample.class.getName());";

  public LogProcessor() {
    super(CLASS_NAME, PsiField.class, LOGGER_DEFINITION);
  }
}
