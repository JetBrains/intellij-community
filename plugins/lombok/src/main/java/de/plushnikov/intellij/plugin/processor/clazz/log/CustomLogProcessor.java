package de.plushnikov.intellij.plugin.processor.clazz.log;

import java.util.List;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.log.CustomLogParser.LoggerInitializerDeclaration;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Adam Juraszek
 */
public class CustomLogProcessor extends AbstractLogProcessor {

  public CustomLogProcessor() {
    super(CustomLog.class);
  }

  @NotNull
  private static String getCustomDeclaration(@NotNull PsiClass psiClass) {
    return ConfigDiscovery.getInstance().getStringLombokConfigProperty(ConfigKey.LOG_CUSTOM_DECLARATION, psiClass);
  }

  @Nullable
  @Override
  public String getLoggerType(@NotNull PsiClass psiClass) {
    return CustomLogParser.parseLoggerType(getCustomDeclaration(psiClass));
  }

  @NotNull
  @Override
  String getLoggerInitializer(@NotNull PsiClass psiClass) {
    String loggerInitializer = CustomLogParser.parseLoggerInitializer(getCustomDeclaration(psiClass));
    if (loggerInitializer == null) {
      throw new IllegalStateException("Invalid custom log declaration."); // validated
    }
    return loggerInitializer;
  }

  @NotNull
  @Override
  List<LoggerInitializerParameter> getLoggerInitializerParameters(@NotNull PsiClass psiClass, boolean topicPresent) {
    LoggerInitializerDeclaration declaration = CustomLogParser.parseInitializerParameters(getCustomDeclaration(psiClass));
    if (declaration == null) {
      throw new IllegalStateException("Invalid custom log declaration."); // validated
    }

    if (!declaration.has(topicPresent)) {
      throw new IllegalStateException("@CustomLog is not configured to work " + (topicPresent ? "with" : "without") + " topic.");
    }
    return declaration.get(topicPresent);
  }

  @Override
  protected boolean validate(
    @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder
  ) {
    if (!super.validate(psiAnnotation, psiClass, builder)) {
      return false;
    }

    final LoggerInitializerDeclaration declaration = CustomLogParser.parseInitializerParameters(getCustomDeclaration(psiClass));
    if (declaration == null) {
      builder.addError("The @CustomLog is not configured correctly; please set log.custom.declaration in lombok.config.");
      return false;
    }
    final String topic = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "topic");
    final boolean topicPresent = !StringUtil.isEmptyOrSpaces(topic);
    if (topicPresent) {
      if (!declaration.hasWithTopic()) {
        builder.addError("@CustomLog does not allow a topic.");
        return false;
      }
    } else {
      if (!declaration.hasWithoutTopic()) {
        builder.addError("@CustomLog requires a topic.");
        return false;
      }
    }
    return true;
  }

}
