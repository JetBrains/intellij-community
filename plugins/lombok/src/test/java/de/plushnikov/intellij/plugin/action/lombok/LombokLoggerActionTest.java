package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.lombok.backend.action.lombok.LombokLoggerAction;
import com.intellij.lombok.shared.LombokBundle;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ConflictInterceptor;
import com.intellij.ui.UiInterceptors;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokActionFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokActionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestApplication
public class LombokLoggerActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/lombok/log", () -> new LombokLoggerAction());

  @Test
  public void testLogSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testLogCustomDeclarationWithBuiltInType() throws Exception {
    lombokAction.get().getFixture().addFileToProject("lombok.config", """
      lombok.log.custom.declaration = java.util.logging.Logger.getLogger(NAME)
      config.stopBubbling = true
      """);
    lombokAction.get().doTest();
  }

  @Test
  public void testLogRename() throws Exception {
    doTestWithConflicts("log324");
  }

  @Test
  public void testLogRenameHidesOuterField() throws Exception {
    addJavaUtilLogger();
    doTestWithConflicts("log324");
    EdtTestUtil.runInEdtAndWait(() -> lombokAction.get().getFixture().checkHighlighting(false, false, false));
  }

  @Test
  public void testLogRenameQualifiedReference() throws Exception {
    addJavaUtilLogger();
    doTestWithConflicts("log324");
    EdtTestUtil.runInEdtAndWait(() -> lombokAction.get().getFixture().checkHighlighting(false, false, false));
  }

  @Test
  public void testLogRenameWithoutQualifier() {
    var fixture = lombokAction.get().getFixture();
    var text = """
      class Test {
        Object object = new Object() {
          private static final java.util.logging.Logger <caret>LOGGER = java.util.logging.Logger.getLogger("test");

          void logMessage(String log) {
            LOGGER.info(log);
          }
        };
      }
      """;
    fixture.configureByText("Test.java", text);

    var exception = assertThrows(RuntimeException.class,
                                 () -> EdtTestUtil.runInEdtAndWait(() -> fixture.testAction(new LombokLoggerAction())));
    assertEquals(LombokBundle.message("dialog.message.logger.cannot.update.references"), exception.getMessage());
    EdtTestUtil.runInEdtAndWait(() -> fixture.checkResult(text));
  }

  @Test
  public void testLogPublic() throws Exception {
    doTestWithConflicts("log");
  }

  @Test
  public void testLogNonStatic() throws Exception {
    doTestWithConflicts("log");
  }

  @Test
  public void testLogNonFinal() throws Exception {
    doTestWithConflicts("log");
  }

  @Test
  public void testLogMultiple() throws Exception {
    doTestWithConflicts("firstLog", "secondLog");
  }

  private void addJavaUtilLogger() {
    lombokAction.get().getFixture().addClass("""
      package java.util.logging;
      public class Logger {
        public static Logger getLogger(String name) {
          return null;
        }

        public void info(String message) {
        }
      }
      """);
  }

  private void doTestWithConflicts(String... loggerFieldNames) throws Exception {
    List<String> conflicts = new ArrayList<>();
    for (String loggerFieldName : loggerFieldNames) {
      conflicts.add(LombokBundle.message("dialog.message.logger.field.s.not.private.sfinal.field.named.s.refactor.anyway",
                                         loggerFieldName, 1, "log"));
    }
    UiInterceptors.register(new ConflictInterceptor(conflicts));
    lombokAction.get().doTest();
  }
}
