package de.plushnikov.intellij.plugin;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.ComparisonFailure;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class AbstractLombokLightCodeInsightTestCase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return ".";
  }

  @Override
  protected String getBasePath() {
    return "testData";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.getProjectDescriptor();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadLombokLibrary();
  }

  protected void loadLombokLibrary() {
    LombokTestUtil.loadLombokLibrary(myFixture.getProjectDisposable(), getModule());
  }

  protected PsiFile loadToPsiFile(String fileName) {
    final String sourceFilePath = getBasePath() + "/" + fileName;
    VirtualFile virtualFile = myFixture.copyFileToProject(sourceFilePath, fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    return myFixture.getFile();
  }

  protected void checkResultByFile(String expectedFile) throws IOException {
    try {
      myFixture.checkResultByFile(expectedFile, true);
    } catch (ComparisonFailure ex) {
      String actualFileText = myFixture.getFile().getText();
      actualFileText = actualFileText.replace("java.lang.", "");

      final String path = getTestDataPath() + "/" + expectedFile;
      String expectedFileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(path)));

      if (!expectedFileText.replaceAll("\\s+", "").equals(actualFileText.replaceAll("\\s+", ""))) {
        assertEquals(expectedFileText, actualFileText);
      }
    }
  }
}
