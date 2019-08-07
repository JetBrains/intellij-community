package de.plushnikov.intellij.plugin.processor.clazz.log;

import java.util.Collections;
import java.util.List;

import com.intellij.psi.PsiClass;
import lombok.extern.flogger.Flogger;
import org.jetbrains.annotations.NotNull;

public class FloggerProcessor extends AbstractSimpleLogProcessor {
  private static final String LOGGER_TYPE = "com.google.common.flogger.FluentLogger";
  private static final String LOGGER_INITIALIZER = "com.google.common.flogger.FluentLogger.forEnclosingClass()";

  public FloggerProcessor() {
    super(Flogger.class, LOGGER_TYPE, LOGGER_INITIALIZER);
  }

  @NotNull
  @Override
  List<LoggerInitializerParameter> getLoggerInitializerParameters(@NotNull PsiClass psiClass, boolean topicPresent) {
    if (topicPresent) {
      throw new IllegalStateException("Flogger does not allow to set a topic.");
    }
    return Collections.emptyList();
  }
}
