package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.core.MavenCore;

import java.io.File;

public class MavenIndicesTestFixture {
  private File myDir;
  private Project myProject;

  private MavenWithDataTestFixture myDataTestFixture;
  private MavenIndicesManager myIndicesManager;

  public MavenIndicesTestFixture(File dir, Project project) {
    myDir = dir;
    myProject = project;
  }

  public void setUp() throws Exception {
    myDataTestFixture = new MavenWithDataTestFixture(myDir);
    myDataTestFixture.setUp();

    FileUtil.copyDir(new File(myDataTestFixture.getTestDataPath("local2")),
                     new File(myDataTestFixture.getTestDataPath("local1")));

    MavenCore.getInstance(myProject).getState().setLocalRepository(myDataTestFixture.getTestDataPath("local1"));

    myIndicesManager = MavenIndicesManager.getInstance();
    myIndicesManager.doInit(new File(myDir, "MavenIndices"));
    myIndicesManager.initProjectIndices(myProject);
  }

  public void tearDown() throws Exception {
    myIndicesManager.doShutdown();
  }

  public MavenIndicesManager getIndicesManager() {
    return myIndicesManager;
  }
}