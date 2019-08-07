package de.plushnikov.intellij.plugin.processor.clazz.log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor.LoggerInitializerParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Never throws exception, returns null in case of invalid declaration.
 *
 * @author Adam Juraszek
 */
class CustomLogParser {
  static class LoggerInitializerDeclaration {
    private final List<LoggerInitializerParameter> withTopic;
    private final List<LoggerInitializerParameter> withoutTopic;

    LoggerInitializerDeclaration(List<LoggerInitializerParameter> withTopic, List<LoggerInitializerParameter> withoutTopic) {
      this.withTopic = withTopic;
      this.withoutTopic = withoutTopic;
    }

    boolean hasWithTopic() {
      return withTopic != null;
    }

    boolean hasWithoutTopic() {
      return withoutTopic != null;
    }

    List<LoggerInitializerParameter> get(boolean topicPresent) {
      return topicPresent ? withTopic : withoutTopic;
    }

    boolean has(boolean topicPresent) {
      return (topicPresent ? withTopic : withoutTopic) != null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LoggerInitializerDeclaration that = (LoggerInitializerDeclaration) o;
      return Objects.equals(withTopic, that.withTopic) && Objects.equals(withoutTopic, that.withoutTopic);
    }

    @Override
    public int hashCode() {
      return Objects.hash(withTopic, withoutTopic);
    }
  }

  // same as in lombok.core.configuration.LogDeclaration
  private static final Pattern PARAMETERS_PATTERN = Pattern.compile("(?:\\(([A-Z,]*)\\))");
  private static final Pattern DECLARATION_PATTERN =
    Pattern.compile("^(?:([^ ]+) )?([^(]+)\\.([^(]+)(" + PARAMETERS_PATTERN.pattern() + "+)$");

  private CustomLogParser() {
    throw new UnsupportedOperationException("Utility class");
  }

  @Nullable
  static String parseLoggerType(@NotNull String customDeclaration) {
    final Matcher declarationMatcher = DECLARATION_PATTERN.matcher(customDeclaration);
    if (!declarationMatcher.matches()) {
      return null;
    }
    String loggerType = declarationMatcher.group(1);
    if (loggerType == null) {
      loggerType = declarationMatcher.group(2);
    }
    return loggerType;
  }

  @Nullable
  static String parseLoggerInitializer(@NotNull String customDeclaration) {
    final Matcher declarationMatcher = DECLARATION_PATTERN.matcher(customDeclaration);
    if (!declarationMatcher.matches()) {
      return null;
    }
    return declarationMatcher.group(2) + "." + declarationMatcher.group(3) + "(%s)";
  }

  /**
   * This method can be used for validating the custom declaration based on returned value.
   *
   * @return null if declaration is invalid
   */
  @Nullable
  static LoggerInitializerDeclaration parseInitializerParameters(@NotNull String customDeclaration) {
    final Matcher declarationMatcher = DECLARATION_PATTERN.matcher(customDeclaration);
    if (!declarationMatcher.matches()) {
      return null;
    }

    List<LoggerInitializerParameter> withTopic = null;
    List<LoggerInitializerParameter> withoutTopic = null;

    final Matcher allParametersMatcher = PARAMETERS_PATTERN.matcher(declarationMatcher.group(4));
    while (allParametersMatcher.find()) {
      final List<LoggerInitializerParameter> splitParameters = splitParameters(allParametersMatcher.group(1));
      if (splitParameters.contains(LoggerInitializerParameter.UNKNOWN)) {
        return null;
      }
      if (splitParameters.contains(LoggerInitializerParameter.TOPIC)) {
        if (withTopic != null) {
          return null;
        }
        withTopic = splitParameters;
      } else {
        if (withoutTopic != null) {
          return null;
        }
        withoutTopic = splitParameters;
      }
    }
    return new LoggerInitializerDeclaration(withTopic, withoutTopic);
  }

  @NotNull
  private static List<LoggerInitializerParameter> splitParameters(@NotNull String parameters) {
    if (parameters.isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.stream(parameters.split(",")).map(LoggerInitializerParameter::find).collect(Collectors.toList());
  }
}
