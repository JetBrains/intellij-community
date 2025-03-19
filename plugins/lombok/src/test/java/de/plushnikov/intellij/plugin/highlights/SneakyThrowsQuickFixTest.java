package de.plushnikov.intellij.plugin.highlights;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import org.intellij.lang.annotations.Language;

import java.util.List;

public class SneakyThrowsQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  public void testSneakyThrowsInvalidQuickFix() {
    myFixture.enableInspections(LombokInspection.class);

    @Language("JAVA") final String text = """
      import lombok.SneakyThrows;
      import java.io.IOException;

      public class TestSneakyThrow {

          @SneakyThrows<caret>
          public TestSneakyThrow(String s) {
              this(throwException());
          }

          public TestSneakyThrow(int i) {
          }

          private static int throwException() throws IOException {
              throw new IOException();
          }
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    myFixture.launchAction("Remove annotation");

    @Language("JAVA") final String expectedText = """
      import java.io.IOException;

      public class TestSneakyThrow {

          public TestSneakyThrow(String s) {
              this(throwException());
          }

          public TestSneakyThrow(int i) {
          }

          private static int throwException() throws IOException {
              throw new IOException();
          }
      }
      """;
    myFixture.checkResult(expectedText, true);
  }

  public void testSneakyThrowsUnhandledExceptionWithoutQuickFix() {
    @Language("JAVA") final String text = """
      import java.io.IOException;

      public class TestSneakyThrow {

            public TestSneakyThrow(String s) {
                this(<caret>throwException());
            }

            public TestSneakyThrow(int i) {
            }

            private static int throwException() throws IOException {
                throw new IOException();
            }
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    List<IntentionAction> actions = myFixture.filterAvailableIntentions("Annotate constructor 'TestSneakyThrow()' as '@SneakyThrows'");
    assertEmpty(actions);
  }

  public void testSneakyThrowsUnhandledExceptionWithQuickFix() {
    @Language("JAVA") final String text = """
      import java.io.IOException;

      public class TestSneakyThrow {

          public TestSneakyThrow(String s) {
              this(throwException());
              if (1 == 1) {
                  throw new <caret>Exception("123");
              }
          }

          public TestSneakyThrow(int i) {
          }

          private static int throwException() throws IOException {
              throw new IOException();
          }
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    myFixture.launchAction("Annotate constructor 'TestSneakyThrow()' as '@SneakyThrows'");

    @Language("JAVA") final String expectedText = """
      import lombok.SneakyThrows;

      import java.io.IOException;

      public class TestSneakyThrow {

          @SneakyThrows
          public TestSneakyThrow(String s) {
              this(throwException());
              if (1 == 1) {
                  throw new Exception("123");
              }
          }

          public TestSneakyThrow(int i) {
          }

          private static int throwException() throws IOException {
              throw new IOException();
          }
      }
      """;
    myFixture.checkResult(expectedText, true);
  }
}
