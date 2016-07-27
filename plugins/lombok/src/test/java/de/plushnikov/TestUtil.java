package de.plushnikov;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class TestUtil {
  public static String getTestDataPath(String relativePath) {
    return getTestDataFile(relativePath).getPath() + File.separator;
  }

  public static File getTestDataFile(String relativePath) {
    return new File(getTestDataRoot(), relativePath);
  }

  private static File getTestDataRoot() {
    return new File("testData").getAbsoluteFile();
  }

  @NotNull
  public static String getTestDataPathRelativeToIdeaHome(@NotNull String relativePath) {
    File homePath = new File(PathManager.getHomePath());
    File testDir = new File(getTestDataRoot(), relativePath);

    String relativePathToIdeaHome = FileUtil.getRelativePath(homePath, testDir);
    if (relativePathToIdeaHome == null) {
      throw new RuntimeException("getTestDataPathRelativeToIdeaHome: FileUtil.getRelativePath('" + homePath +
        "', '" + testDir + "') returned null");
    }

    return relativePathToIdeaHome;
  }
}
