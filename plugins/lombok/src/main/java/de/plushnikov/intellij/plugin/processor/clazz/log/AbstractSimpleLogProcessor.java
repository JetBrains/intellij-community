package de.plushnikov.intellij.plugin.processor.clazz.log;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

abstract class AbstractSimpleLogProcessor extends AbstractLogProcessor {
  @NotNull
  private final String loggerType;
  @NotNull
  private final String loggerInitializer;

  AbstractSimpleLogProcessor(
    @NotNull Class<? extends Annotation> supportedAnnotationClass,
    @NotNull String loggerType,
    @NotNull String loggerInitializer
  ) {
    super(supportedAnnotationClass);
    this.loggerType = loggerType;
    this.loggerInitializer = loggerInitializer;
  }

  @NotNull
  @Override
  public final String getLoggerType(@NotNull PsiClass psiClass) {
    return loggerType;
  }

  @NotNull
  @Override
  final String getLoggerInitializer(@NotNull PsiClass psiClass) {
    return loggerInitializer;
  }
}

abstract class AbstractTopicSupportingSimpleLogProcessor extends AbstractSimpleLogProcessor {
  @NotNull
  private final LoggerInitializerParameter defaultParameter;

  AbstractTopicSupportingSimpleLogProcessor(
    @NotNull Class<? extends Annotation> supportedAnnotationClass,
    @NotNull String loggerType,
    @NotNull String loggerInitializer,
    @NotNull LoggerInitializerParameter defaultParameter
  ) {
    super(supportedAnnotationClass, loggerType, loggerInitializer);
    this.defaultParameter = defaultParameter;
  }

  @NotNull
  @Override
  final List<LoggerInitializerParameter> getLoggerInitializerParameters(@NotNull PsiClass psiClass, boolean topicPresent) {
    return Collections.singletonList(topicPresent ? LoggerInitializerParameter.TOPIC : defaultParameter);
  }
}
