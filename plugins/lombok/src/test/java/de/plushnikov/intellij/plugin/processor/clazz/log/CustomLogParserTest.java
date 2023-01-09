package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor.LoggerInitializerParameter;
import de.plushnikov.intellij.plugin.processor.clazz.log.CustomLogParser.LoggerInitializerDeclaration;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


@RunWith(Theories.class)
public class CustomLogParserTest {

  @DataPoints("invalid")
  public static String[] invalid() {
    return new String[] {
      "", "abc", "abc def ()", "C.m()()())", "C C.m(123)"
    };
  }

  @DataPoints("invalidButPassing") // lombok itself validates these, do we need to validate them too?
  public static String[] invalidButPassing() {
    return new String[] {
      "123 456.789()", "A   B   .  .  .  ()"
    };
  }

  @DataPoints("validType")
  public static String[] validType() {
    return new String[] {
      "A.m()", "A A.m()", "A.m()(NAME,TOPIC)"
    };
  }

  @Theory
  public void parseLoggerTypeInvalid(@FromDataPoints("invalid") String invalid) {
    assertNull(CustomLogParser.parseLoggerType(invalid));
  }

  @Theory
  public void parseLoggerTypeValid(@FromDataPoints("validType") String valid) {
    assertEquals("A", CustomLogParser.parseLoggerType(valid));
  }

  @DataPoints("validInitializer")
  public static String[] validInitializer() {
    return new String[] {
      "A B.m()", "B.m()", "B.m(NAME,NULL)(TOPIC)"
    };
  }

  @Theory
  public void parseLoggerInitializerInvalid(@FromDataPoints("invalid") String invalid) {
    assertNull(CustomLogParser.parseLoggerInitializer(invalid));
  }

  @Theory
  public void parseLoggerInitializerValid(@FromDataPoints("validInitializer") String valid) {
    assertEquals("B.m(%s)", CustomLogParser.parseLoggerInitializer(valid));
  }

  @Theory
  public void parseInitializerParametersInvalid(@FromDataPoints("invalid") String invalid) {
    assertNull(CustomLogParser.parseInitializerParameters(invalid));
  }

  @DataPoints("invalidParameters")
  public static String[] invalidParameters() {
    return new String[] {
      "B.m(XYZ)", "B.m()()", "B.m(NAME,,NULL)", "B.m(NAME)(NAME,TOPIC)(NULL)", "B.m(NAME)(NAME,TOPIC)(NULL,TOPIC)"
    };
  }

  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument") // makes it more symmetric
  @DataPoints("validParameters")
  public static T[] validParameters() {
    return new T[] {
      t("B.m()", null, asList()),
      t("B.m(NAME)", null, asList(LoggerInitializerParameter.NAME)),
      t("B.m(TYPE)", null, asList(LoggerInitializerParameter.TYPE)),
      t("B.m(NULL)", null, asList(LoggerInitializerParameter.NULL)),

      t("B.m(NAME,NULL,NAME)", null,
        asList(LoggerInitializerParameter.NAME, LoggerInitializerParameter.NULL, LoggerInitializerParameter.NAME)),
      t("B.m(NAME,TOPIC)", asList(LoggerInitializerParameter.NAME, LoggerInitializerParameter.TOPIC), null),
      t("B.m(TOPIC,TOPIC)(NULL)", asList(LoggerInitializerParameter.TOPIC, LoggerInitializerParameter.TOPIC),
        asList(LoggerInitializerParameter.NULL)),
      t("B.m()(TOPIC,TYPE)", asList(LoggerInitializerParameter.TOPIC, LoggerInitializerParameter.TYPE), asList())
    };
  }

  @Theory
  public void parseInitializerParametersInvalid2(@FromDataPoints("invalidParameters") String invalid) {
    assertNull(CustomLogParser.parseInitializerParameters(invalid));
  }

  @Theory
  public void parseInitializerParametersValid(@FromDataPoints("validParameters") T valid) {
    assertEquals(valid.declaration, CustomLogParser.parseInitializerParameters(valid.input));
  }

  // utils


  private static class T {
    private final String input;
    private final LoggerInitializerDeclaration declaration;

    T(String input, LoggerInitializerDeclaration declaration) {
      this.input = input;
      this.declaration = declaration;
    }
  }

  private static T t(String input, List<LoggerInitializerParameter> withTopic, List<LoggerInitializerParameter> withoutTopic) {
    return new T(input, new LoggerInitializerDeclaration(withTopic, withoutTopic));
  }
}
