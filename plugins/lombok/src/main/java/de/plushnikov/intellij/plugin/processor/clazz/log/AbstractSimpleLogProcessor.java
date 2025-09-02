package de.plushnikov.intellij.plugin.processor.clazz.log;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class AbstractSimpleLogProcessor extends AbstractLogProcessor {
  private final @NotNull String loggerType;
  private final @NotNull String loggerInitializer;

  AbstractSimpleLogProcessor(
    @NotNull String supportedAnnotationClass,
    @NotNull String loggerType,
    @NotNull String loggerInitializer
  ) {
    super(supportedAnnotationClass);
    this.loggerType = loggerType;
    this.loggerInitializer = loggerInitializer;
  }

  @Override
  public final @NotNull String getLoggerType(@NotNull PsiClass psiClass) {
    return loggerType;
  }

  @Override
  final @NotNull String getLoggerInitializer(@NotNull PsiClass psiClass) {
    return loggerInitializer;
  }
}
