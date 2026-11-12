package de.plushnikov.intellij.plugin.highlights;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class SneakyThrowsQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testSneakyThrowsInvalidQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testSneakyThrowsUnhandledExceptionWithoutQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    EdtTestUtil.runInEdtAndWait(() -> {
      List<IntentionAction> actions = myFixture.filterAvailableIntentions("Annotate constructor 'TestSneakyThrow()' as '@SneakyThrows'");
      assertThat(actions).isNullOrEmpty();
    });
  }

  @Test
  public void testSneakyThrowsUnhandledExceptionWithQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }
}
