package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class MavenCustomRepositoryTestFixture {
  private File myTempDir;
  private File myWorkingData;

  public MavenCustomRepositoryTestFixture(File tempDir) {
    myTempDir = tempDir;
  }

  public void setUp() throws Exception {
    myWorkingData = new File(myTempDir, "testData");
    FileUtil.copyDir(new File(getOriginalTestDataPath()), myWorkingData);
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
