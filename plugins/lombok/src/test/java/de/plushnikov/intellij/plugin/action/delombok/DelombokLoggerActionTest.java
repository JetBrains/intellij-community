package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokActionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokActionFixture;

@TestApplication
public class DelombokLoggerActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/delombok/log", () -> new DelombokLoggerAction());

  @Test
  public void testLog() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = lombokAction.get().getFixture();
    // java.util.logging.Logger is not in mockJdk21
    myFixture.addClass("""
                         package java.util.logging;
                         public class Logger {
                           public static Logger getLogger(String name) {
                                 return null;
                             }
                         }""");
    lombokAction.get().doTest();
  }

  @Test
  public void testLog4j() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testLog4j2() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testCommonsLog() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testSlf4j() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testXSlf4j() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testJBossLog() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testFlogger() throws Exception {
    lombokAction.get().doTest();
  }
}
