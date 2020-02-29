package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.rename.RenameProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class LombokLoggerHandler extends BaseLombokHandler {

  protected void processClass(@NotNull PsiClass psiClass) {
    final Collection<AbstractLogProcessor> logProcessors = Arrays.asList(
      ServiceManager.getService(CommonsLogProcessor.class), ServiceManager.getService(JBossLogProcessor.class),
      ServiceManager.getService(Log4jProcessor.class), ServiceManager.getService(Log4j2Processor.class), ServiceManager.getService(LogProcessor.class),
      ServiceManager.getService(Slf4jProcessor.class), ServiceManager.getService(XSlf4jProcessor.class), ServiceManager.getService(FloggerProcessor.class),
      ServiceManager.getService(CustomLogProcessor.class));

    final String lombokLoggerName = AbstractLogProcessor.getLoggerName(psiClass);
    final boolean lombokLoggerIsStatic = AbstractLogProcessor.isLoggerStatic(psiClass);

    for (AbstractLogProcessor logProcessor : logProcessors) {
      for (PsiField psiField : psiClass.getFields()) {
        String loggerType = logProcessor.getLoggerType(psiClass); // null when the custom log's declaration is invalid
        if (loggerType != null && psiField.getType().equalsToText(loggerType)
          && checkLoggerField(psiField, lombokLoggerName, lombokLoggerIsStatic)) {
          processLoggerField(psiField, psiClass, logProcessor, lombokLoggerName);
        }
      }
    }
  }

  private void processLoggerField(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull AbstractLogProcessor logProcessor, @NotNull String lombokLoggerName) {
    if (!lombokLoggerName.equals(psiField.getName())) {
      RenameProcessor processor = new RenameProcessor(psiField.getProject(), psiField, lombokLoggerName, false, false);
      processor.doRun();
    }

    addAnnotation(psiClass, logProcessor.getSupportedAnnotationClasses()[0]);

    psiField.delete();
  }

  private boolean checkLoggerField(@NotNull PsiField psiField, @NotNull String lombokLoggerName, boolean lombokLoggerIsStatic) {
    if (!isValidLoggerField(psiField, lombokLoggerName, lombokLoggerIsStatic)) {
      final String messageText = String.format("Logger field: \"%s\" Is not private %sfinal field named \"%s\". Refactor anyway?",
        psiField.getName(), lombokLoggerIsStatic ? "static " : "", lombokLoggerName);
      int result = Messages.showOkCancelDialog(messageText, "Attention!", Messages.getOkButton(), Messages.getCancelButton(), Messages.getQuestionIcon());
      return DialogWrapper.OK_EXIT_CODE == result;
    }
    return true;
  }

  private boolean isValidLoggerField(@NotNull PsiField psiField, @NotNull String lombokLoggerName, boolean lombokLoggerIsStatic) {
    boolean isPrivate = psiField.hasModifierProperty(PsiModifier.PRIVATE);
    boolean isStatic = lombokLoggerIsStatic == psiField.hasModifierProperty(PsiModifier.STATIC);
    boolean isFinal = psiField.hasModifierProperty(PsiModifier.FINAL);
    boolean isProperlyNamed = lombokLoggerName.equals(psiField.getName());

    return isPrivate && isStatic && isFinal && isProperlyNamed;
  }
}
