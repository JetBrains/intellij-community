package de.plushnikov.intellij.plugin.handler;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.rename.RenameProcessor;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public final class LombokLoggerHandler extends BaseLombokHandler {
  @Override
  protected void processClass(@NotNull PsiClass psiClass) {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();
    final Collection<AbstractLogProcessor> logProcessors = Arrays.asList(
      manager.getCommonsLogProcessor(),
      manager.getJBossLogProcessor(),
      manager.getLog4jProcessor(),
      manager.getLog4j2Processor(),
      manager.getLogProcessor(),
      manager.getSlf4jProcessor(),
      manager.getXSlf4jProcessor(),
      manager.getFloggerProcessor(),
      manager.getCustomLogProcessor());

    final String lombokLoggerName = AbstractLogProcessor.getLoggerName(psiClass);
    final boolean lombokLoggerIsStatic = AbstractLogProcessor.isLoggerStatic(psiClass);

    for (AbstractLogProcessor logProcessor : logProcessors) {
      String loggerType = logProcessor.getLoggerType(psiClass); // null when the custom log's declaration is invalid
      if (loggerType == null) {
        continue;
      }
      for (PsiField psiField : psiClass.getFields()) {
        if (psiField.getType().equalsToText(loggerType) && checkLoggerField(psiField, lombokLoggerName, lombokLoggerIsStatic)) {
          processLoggerField(psiField, psiClass, logProcessor, lombokLoggerName);
        }
      }
    }
  }

  private static void processLoggerField(@NotNull PsiField psiField,
                                         @NotNull PsiClass psiClass,
                                         @NotNull AbstractLogProcessor logProcessor,
                                         @NotNull String lombokLoggerName) {
    if (!lombokLoggerName.equals(psiField.getName())) {
      RenameProcessor processor = new RenameProcessor(psiField.getProject(), psiField, lombokLoggerName, false, false);
      processor.doRun();
    }

    addAnnotation(psiClass, logProcessor.getSupportedAnnotationClasses()[0]);

    psiField.delete();
  }

  private static boolean checkLoggerField(@NotNull PsiField psiField, @NotNull String lombokLoggerName, boolean lombokLoggerIsStatic) {
    if (!isValidLoggerField(psiField, lombokLoggerName, lombokLoggerIsStatic)) {
      String messageText =
        LombokBundle.message("dialog.message.logger.field.s.not.private.sfinal.field.named.s.refactor.anyway", psiField.getName(),
                             lombokLoggerIsStatic ? 1 : 0, lombokLoggerName);
      int result = Messages.showOkCancelDialog(messageText, LombokBundle.message("dialog.title.attention"), Messages.getOkButton(), Messages.getCancelButton(), Messages.getQuestionIcon());
      return DialogWrapper.OK_EXIT_CODE == result;
    }
    return true;
  }

  private static boolean isValidLoggerField(@NotNull PsiField psiField, @NotNull String lombokLoggerName, boolean lombokLoggerIsStatic) {
    boolean isPrivate = psiField.hasModifierProperty(PsiModifier.PRIVATE);
    boolean isStatic = lombokLoggerIsStatic == psiField.hasModifierProperty(PsiModifier.STATIC);
    boolean isFinal = psiField.hasModifierProperty(PsiModifier.FINAL);
    boolean isProperlyNamed = lombokLoggerName.equals(psiField.getName());

    return isPrivate && isStatic && isFinal && isProperlyNamed;
  }
}
