package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.lombok.backend.action.lombok.LombokLoggerAction;
import com.intellij.lombok.shared.LombokBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.ConflictInterceptor;
import com.intellij.ui.UiInterceptors;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

import java.util.ArrayList;
import java.util.List;

public class LombokLoggerActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new LombokLoggerAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/lombok/log";
  }

  public void testLogSimple() throws Exception {
    doTest();
  }

  public void testLogCustomDeclarationWithBuiltInType() throws Exception {
    myFixture.addFileToProject("lombok.config", """
      lombok.log.custom.declaration = java.util.logging.Logger.getLogger(NAME)
      config.stopBubbling = true
      """);
    doTest();
  }

  public void testLogRename() throws Exception {
    doTestWithConflicts("log324");
  }

  public void testLogRenameHidesOuterField() throws Exception {
    addJavaUtilLogger();
    doTestWithConflicts("log324");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testLogRenameQualifiedReference() throws Exception {
    addJavaUtilLogger();
    doTestWithConflicts("log324");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testLogRenameWithoutQualifier() {
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
    myFixture.configureByText("Test.java", text);

    assertThrows(() -> myFixture.testAction(getAction()), RuntimeException.class,
                 message -> assertEquals(LombokBundle.message("dialog.message.logger.cannot.update.references"), message));
    myFixture.checkResult(text);
  }

  public void testLogPublic() throws Exception {
    doTestWithConflicts("log");
  }

  public void testLogNonStatic() throws Exception {
    doTestWithConflicts("log");
  }

  public void testLogNonFinal() throws Exception {
    doTestWithConflicts("log");
  }

  public void testLogMultiple() throws Exception {
    doTestWithConflicts("firstLog", "secondLog");
  }

  private void addJavaUtilLogger() {
    myFixture.addClass("""
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
    doTest();
  }
}
