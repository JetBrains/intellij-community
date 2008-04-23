package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.idea.maven.MavenTestCase;

import java.io.File;

public abstract class MavenWithDataTestCase extends MavenTestCase {
  private File workingData;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    workingData = new File(myDir, "testData");
    FileUtil.copyDir(new File(getOriginalTestDataPath()), workingData);
  }

  private String getOriginalTestDataPath() {
    String path = PathManager.getHomePath() + "/svnPlugins/maven/src/test/data";
    return FileUtil.toSystemIndependentName(path);
  }

  protected String getTestDataPath(String relativePath) {
    String path = new File(workingData, relativePath).getPath();
    return FileUtil.toSystemIndependentName(path);
  }}
