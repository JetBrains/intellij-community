package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class MavenCustomRepositoryHelper {
  private File myTempDir;
  private File myWorkingData;
  private String[] mySubFolders;

  public MavenCustomRepositoryHelper(File tempDir, String... subFolders) throws IOException {
    myTempDir = tempDir;
    mySubFolders = subFolders;

    myWorkingData = new File(myTempDir, "testData");

    for (String each : mySubFolders) {
      addTestData(each);
    }
  }

  public void addTestData(String relativePath) throws IOException {
    FileUtil.copyDir(new File(getOriginalTestDataPath(), relativePath), new File(myWorkingData, relativePath));
  }

  private String getOriginalTestDataPath() {
    String path = PathManager.getHomePath() + "/svnPlugins/maven/src/test/data";
    return FileUtil.toSystemIndependentName(path);
  }

  public String getTestDataPath(String relativePath) {
    String path = getTestData(relativePath).getPath();
    return FileUtil.toSystemIndependentName(path);
  }

  public File getTestData(String relativePath) {
    return new File(myWorkingData, relativePath);
  }

  public void delete(String relativePath) {
    FileUtil.delete(new File(getTestDataPath(relativePath)));
  }

  public void copy(String fromRelativePath, String toRelativePath) throws IOException {
    FileUtil.copyDir(new File(getTestDataPath(fromRelativePath)),
                     new File(getTestDataPath(toRelativePath)));
  }
}
