package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokConfigSystemFixture;

@TestApplication
public class GetterDumbModeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  //use normal mode, because it is configured manually in tests
  private final TestFixture<LombokParsingFixture> parsing = lombokConfigSystemFixture(projectFixture, "/configsystem/getter");

  @Test
  public void testDumb$GetterClassTest() {
    LombokParsingFixture configSystem = parsing.get();
    JavaCodeInsightTestFixture myFixture = configSystem.getFixture();
    final String fullFileName = configSystem.getTestName(true).replace('$', '/') + ".java";
    final int lastIndexOf = fullFileName.lastIndexOf('/');
    final String subPath = fullFileName.substring(0, lastIndexOf);
    final String fileName = fullFileName.substring(lastIndexOf + 1);

    DumbModeTestUtils.runInDumbModeSynchronously(
      myFixture.getProject(),
      () -> {
        myFixture.copyFileToProject(subPath + "/before/lombok.config", subPath + "/before/lombok.config");
        configSystem.doTest(subPath + "/before/inner/" + fileName, subPath + "/after/inner/" + fileName);
      }
    );
  }

  @Test
  public void testDumbStopBubbling$GetterClassTest() {
    LombokParsingFixture configSystem = parsing.get();
    JavaCodeInsightTestFixture myFixture = configSystem.getFixture();
    final String fullFileName = configSystem.getTestName(true).replace('$', '/') + ".java";
    final int lastIndexOf = fullFileName.lastIndexOf('/');
    final String subPath = fullFileName.substring(0, lastIndexOf);
    final String fileName = fullFileName.substring(lastIndexOf + 1);

    DumbModeTestUtils.runInDumbModeSynchronously(
      myFixture.getProject(),
      () -> {
        myFixture.copyFileToProject(subPath + "/before/inner/lombok.config", subPath + "/before/inner/lombok.config");
        myFixture.copyFileToProject(subPath + "/before/lombok.config", subPath + "/before/lombok.config");
        configSystem.doTest(subPath + "/before/inner/" + fileName, subPath + "/after/inner/" + fileName);
      }
    );
  }
}