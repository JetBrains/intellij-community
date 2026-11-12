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
public class DelombokEqualsAndHashcodeActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/delombok/equalsandhashcode", () -> new DelombokEqualsAndHashCodeAction());

  @Test
  public void testEqualsAndHashCodeSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testEqualsAndHashCodeOf() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testEqualsAndHashCodeNoGetters() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testEqualsAndHashCodeExclude() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testEqualsAndHashCodeCallSuper() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testEqualsAndHashCodeIncludeExclude() throws Exception {
    lombokAction.get().doTest();
  }
}
