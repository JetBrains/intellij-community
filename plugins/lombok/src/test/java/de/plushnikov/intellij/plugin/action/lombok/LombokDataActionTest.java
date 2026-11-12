package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.lombok.backend.action.lombok.LombokDataAction;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokActionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokActionFixture;

@TestApplication
public class LombokDataActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/lombok/data", () -> new LombokDataAction());

  @Test
  public void testDataSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testDataWithCustomCodeSetterGetter() throws Exception {
    lombokAction.get().doTest();
  }
}
