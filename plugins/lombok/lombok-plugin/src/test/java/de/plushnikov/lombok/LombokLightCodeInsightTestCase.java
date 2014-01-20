package de.plushnikov.lombok;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Date: 20.01.14 Time: 20:27
 */
public class LombokLightCodeInsightTestCase extends LightCodeInsightFixtureTestCase {
  private static final String LOMBOK_SRC_PATH = "./lombok-api/target/generated-sources/lombok";
  private static final String LOMBOKPG_SRC_PATH = "./lombok-api/target/generated-sources/lombok-pg";

  @Override
  protected String getTestDataPath() {
    return ".";
  }

  @Override
  protected String getBasePath() {
    return "lombok-plugin/src/test/data";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    addLombokClassesToFixture();
  }

  private void addLombokClassesToFixture() {
    loadFilesFrom(LOMBOK_SRC_PATH);
    loadFilesFrom(LOMBOKPG_SRC_PATH);
  }

  private void loadFilesFrom(final String srcPath) {
    List<File> filesByMask = FileUtil.findFilesByMask(Pattern.compile(".*\\.java"), new File(srcPath));
    for (File javaFile : filesByMask) {
      String filePath = javaFile.getPath().replace("\\", "/");
      myFixture.copyFileToProject(filePath, filePath.substring(srcPath.length() + 1));
    }
  }

  protected PsiFile loadToPsiFile(String fileName) {
    VirtualFile virtualFile = myFixture.copyFileToProject(getBasePath() + "/" + fileName, fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    return myFixture.getFile();
  }
}
