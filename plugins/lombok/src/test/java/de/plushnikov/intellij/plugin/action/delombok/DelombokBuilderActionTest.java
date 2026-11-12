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
public class DelombokBuilderActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/delombok/builder", () -> new DelombokBuilderAction());

  @Test
  public void testBuilderSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderSimplePredefined() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderAtMethodSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderAtMethodSimplePredefined() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderAtConstructorSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderAtConstructorSimplePredefined() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderSingularMap() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderSimpleWithSetterPrefix() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderCopyableAnnotations() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testBuilderJacksonized() throws Exception {
    lombokAction.get().doTest();
  }
}
