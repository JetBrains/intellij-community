package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.lombok.backend.action.lombok.LombokSetterAction;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokActionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokActionFixture;

@TestApplication
public class LombokSetterActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/lombok/setter", () -> new LombokSetterAction());

  @Test
  public void testSetterSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testSetterStatic() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testSetterDifferentVisibility() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testSetterWithAnnotationPresentOnClass() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testSetterWithAnnotationChange() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testSetterWithCustomCode() throws Exception {
    lombokAction.get().doTest();
  }
}
