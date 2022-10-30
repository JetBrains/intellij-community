package de.plushnikov.intellij.plugin.processor.clazz.log;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Base lombok processor class for logger processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLogProcessor extends AbstractClassProcessor {
  enum LoggerInitializerParameter {
    TYPE,
    NAME,
    TOPIC,
    NULL,
    UNKNOWN;

    @NotNull
    static LoggerInitializerParameter find(@NotNull String parameter) {
      return switch (parameter) {
        case "TYPE" -> TYPE;
        case "NAME" -> NAME;
        case "TOPIC" -> TOPIC;
        case "NULL" -> NULL;
        default -> UNKNOWN;
      };
    }
  }

  AbstractLogProcessor(@NotNull String supportedAnnotationClass) {
    super(PsiField.class, supportedAnnotationClass);
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    return nameHint == null || nameHint.equals(getLoggerName(psiClass));
  }

  @NotNull
  public static String getLoggerName(@NotNull PsiClass psiClass) {
    return ConfigDiscovery.getInstance().getStringLombokConfigProperty(ConfigKey.LOG_FIELDNAME, psiClass);
  }

  public static boolean isLoggerStatic(@NotNull PsiClass psiClass) {
    return ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(ConfigKey.LOG_FIELD_IS_STATIC, psiClass);
  }

  /**
   * Nullable because it can be called before validation.
   */
  @Nullable
  public abstract String getLoggerType(@NotNull PsiClass psiClass);

  /**
   * Call only after validation.
   */
  @NotNull
  abstract String getLoggerInitializer(@NotNull PsiClass psiClass);

  /**
   * Call only after validation.
   */
  @NotNull
  abstract List<LoggerInitializerParameter> getLoggerInitializerParameters(@NotNull PsiClass psiClass, boolean topicPresent);

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    boolean result = true;
    if (psiClass.isInterface() || psiClass.isAnnotationType()) {
      builder.addErrorMessage("inspection.message.s.legal.only.on.classes.enums", getSupportedAnnotationClasses()[0]);
      result = false;
    }
    if (result) {
      final String loggerName = getLoggerName(psiClass);
      if (hasFieldByName(psiClass, loggerName)) {
        builder.addErrorMessage("inspection.message.not.generating.field.s.field.with.same.name.already.exists", loggerName);
        result = false;
      }
    }
    return result;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target) {
    target.add(createLoggerField(psiClass, psiAnnotation));
  }

  private LombokLightFieldBuilder createLoggerField(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    // called only after validation succeeded
    final Project project = psiClass.getProject();
    final PsiManager manager = psiClass.getContainingFile().getManager();

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
    String loggerType = getLoggerType(psiClass);
    if (loggerType == null) {
      throw new IllegalStateException("Invalid custom log declaration."); // validated
    }
    final PsiType psiLoggerType = psiElementFactory.createTypeFromText(loggerType, psiClass);

    LombokLightFieldBuilder loggerField = new LombokLightFieldBuilder(manager, getLoggerName(psiClass), psiLoggerType)
      .withContainingClass(psiClass)
      .withModifier(PsiModifier.FINAL)
      .withModifier(PsiModifier.PRIVATE)
      .withNavigationElement(psiAnnotation);
    if (isLoggerStatic(psiClass)) {
      loggerField.withModifier(PsiModifier.STATIC);
    }

    final String loggerInitializerParameters = createLoggerInitializeParameters(psiClass, psiAnnotation);
    final String initializerText = String.format(getLoggerInitializer(psiClass), loggerInitializerParameters);
    final PsiExpression initializer = psiElementFactory.createExpressionFromText(initializerText, psiClass);
    loggerField.setInitializer(initializer);
    return loggerField;
  }

  @NotNull
  private String createLoggerInitializeParameters(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final StringBuilder parametersBuilder = new StringBuilder();
    final String topic = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "topic", "");
    final boolean topicPresent = !StringUtil.isEmptyOrSpaces(topic);
    final List<LoggerInitializerParameter> loggerInitializerParameters = getLoggerInitializerParameters(psiClass, topicPresent);
    for (LoggerInitializerParameter loggerInitializerParameter : loggerInitializerParameters) {
      if (parametersBuilder.length() > 0) {
        parametersBuilder.append(", ");
      }
      switch (loggerInitializerParameter) {
        case TYPE -> parametersBuilder.append(psiClass.getName()).append(".class");
        case NAME -> parametersBuilder.append(psiClass.getName()).append(".class.getName()");
        case TOPIC -> {
          if (!topicPresent) {
            // sanity check; either implementation of CustomLogParser or predefined loggers is wrong
            throw new IllegalStateException("Topic can never be a parameter when topic was not set.");
          }
          parametersBuilder.append('"').append(StringUtil.escapeStringCharacters(topic)).append('"');
        }
        case NULL -> parametersBuilder.append("null");
        default ->
          // sanity check; either implementation of CustomLogParser or predefined loggers is wrong
          throw new IllegalStateException("Unexpected logger initializer parameter " + loggerInitializerParameter);
      }
    }
    return parametersBuilder.toString();
  }

  private static boolean hasFieldByName(@NotNull PsiClass psiClass, @NotNull String fieldName) {
    final Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      if (fieldName.equals(psiField.getName())) {
        return true;
      }
    }
    return false;
  }
}
