package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.rename.RenameProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.CommonsLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4j2Processor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.LogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.XSlf4jProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class LombokLoggerHandler extends BaseLombokHandler {

  private static final String LOMBOK_LOGGER_NAME = AbstractLogProcessor.getLoggerName();

  protected void processClass(@NotNull PsiClass psiClass) {
    final Collection<AbstractLogProcessor> logProcessors = Arrays.asList(
        new CommonsLogProcessor(), new Log4jProcessor(), new Log4j2Processor(),
        new LogProcessor(), new Slf4jProcessor(), new XSlf4jProcessor());

    for (AbstractLogProcessor logProcessor : logProcessors) {
      for (PsiField psiField : psiClass.getFields()) {
        if (psiField.getType().equalsToText(logProcessor.getLoggerType()) && checkLoggerField(psiField)) {
          processLoggerField(psiField, psiClass, logProcessor);
        }
      }
    }
  }

  private void processLoggerField(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull AbstractLogProcessor logProcessor) {
    if (!LOMBOK_LOGGER_NAME.equals(psiField.getName())) {
      RenameProcessor processor = new RenameProcessor(psiField.getProject(), psiField, LOMBOK_LOGGER_NAME, false, false);
      processor.doRun();
    }

    addAnnotation(psiClass, logProcessor.getSupportedAnnotationClass());

    psiField.delete();
  }

  private boolean checkLoggerField(@NotNull PsiField psiField) {
    if (!isValidLoggerField(psiField)) {
      int result = Messages.showOkCancelDialog(
          String.format("Logger field: \"%s\" Is not private static final field named \"log\". Refactor anyway?", psiField.getName()),
          "Attention!", Messages.getQuestionIcon());
      return DialogWrapper.OK_EXIT_CODE == result;
    }
    return true;
  }

  private boolean isValidLoggerField(@NotNull PsiField psiField) {
    boolean isPrivate = psiField.hasModifierProperty(PsiModifier.PRIVATE);
    boolean isStatic = psiField.hasModifierProperty(PsiModifier.STATIC);
    boolean isFinal = psiField.hasModifierProperty(PsiModifier.FINAL);
    boolean isProperlyNamed = LOMBOK_LOGGER_NAME.equals(psiField.getName());

    return isPrivate & isStatic & isFinal & isProperlyNamed;
  }
}