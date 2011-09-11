package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.psi.PsiField;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Plushnikov Michail
 */
public class Slf4jProcessor extends AbstractLogProcessor {

  private static final String CLASS_NAME = Slf4j.class.getName();
  private static final String LOGGER_DEFINITION = "private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LogExample.class);";

  public Slf4jProcessor() {
    super(CLASS_NAME, PsiField.class, LOGGER_DEFINITION);
  }
}
