package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokActionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokActionFixture;

@TestApplication
public class DelombokValueActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/delombok/value", () -> new DelombokValueAction());

  @Test
  public void testValuePlain() throws Exception {
    lombokAction.get().doTest();
  }
  @Test
  public void testValueWithEAHCExclude() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testValueNonFinalOnField() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testValueNonFinalOnClass() throws Exception {
    lombokAction.get().doTest();
  }
}
