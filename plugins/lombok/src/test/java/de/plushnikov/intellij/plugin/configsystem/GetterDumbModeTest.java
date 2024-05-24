package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.testFramework.DumbModeTestUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GetterDumbModeTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/getter";
  }

  public void testDumb$GetterClassTest() {
    final String fullFileName = getTestName(true).replace('$', '/') + ".java";
    final int lastIndexOf = fullFileName.lastIndexOf('/');
    final String subPath = fullFileName.substring(0, lastIndexOf);
    final String fileName = fullFileName.substring(lastIndexOf + 1);

    DumbModeTestUtils.runInDumbModeSynchronously(
      getProject(),
      () -> {
        myFixture.copyFileToProject(subPath + "/before/lombok.config", subPath + "/before/lombok.config");
        doTest(subPath + "/before/inner/" + fileName, subPath + "/after/inner/" + fileName);
      }
    );
  }
  public void testDumbStopBubbling$GetterClassTest() {
    final String fullFileName = getTestName(true).replace('$', '/') + ".java";
    final int lastIndexOf = fullFileName.lastIndexOf('/');
    final String subPath = fullFileName.substring(0, lastIndexOf);
    final String fileName = fullFileName.substring(lastIndexOf + 1);

    DumbModeTestUtils.runInDumbModeSynchronously(
      getProject(),
      () -> {
        myFixture.copyFileToProject(subPath + "/before/inner/lombok.config", subPath + "/before/inner/lombok.config");
        myFixture.copyFileToProject(subPath + "/before/lombok.config", subPath + "/before/lombok.config");
        doTest(subPath + "/before/inner/" + fileName, subPath + "/after/inner/" + fileName);
      }
    );
  }

  @Override
  protected @NotNull List<ModeRunnerType> modes() {
    //use normal mode, because it is configured manually in tests
    return List.of(ModeRunnerType.NORMAL);
  }
}