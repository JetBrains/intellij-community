package de.plushnikov.intellij.plugin.processor.clazz.log;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public abstract class AbstractTopicSupportingSimpleLogProcessor extends AbstractSimpleLogProcessor {
  private final @NotNull LoggerInitializerParameter defaultParameter;

  AbstractTopicSupportingSimpleLogProcessor(
    @NotNull String supportedAnnotationClass,
    @NotNull String loggerType,
    @NotNull String loggerInitializer,
    @NotNull LoggerInitializerParameter defaultParameter
  ) {
    super(supportedAnnotationClass, loggerType, loggerInitializer);
    this.defaultParameter = defaultParameter;
  }

  @Override
  final @NotNull List<LoggerInitializerParameter> getLoggerInitializerParameters(@NotNull PsiClass psiClass, boolean topicPresent) {
    return Collections.singletonList(topicPresent ? LoggerInitializerParameter.TOPIC : defaultParameter);
  }
}
