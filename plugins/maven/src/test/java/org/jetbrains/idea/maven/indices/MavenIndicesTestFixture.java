package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.core.MavenCore;

import java.io.File;

public class MavenIndicesTestFixture {
  private File myDir;
  private Project myProject;
  private String myLocalRepoDir;
  private String[] myExtraRepoDirs;

  private MavenWithDataTestFixture myDataTestFixture;
  private MavenProjectIndicesManager myIndicesManager;

  public MavenIndicesTestFixture(File dir, Project project) {
    this(dir, project, "local1", "local2");
  }

  public MavenIndicesTestFixture(File dir, Project project, String localRepoDir, String... extraRepoDirs) {
    myDir = dir;
    myProject = project;
    myLocalRepoDir = localRepoDir;
    myExtraRepoDirs = extraRepoDirs;
  }

  public void setUp() throws Exception {
    myDataTestFixture = new MavenWithDataTestFixture(myDir);
    myDataTestFixture.setUp();

    for (String each : myExtraRepoDirs) {
      myDataTestFixture.copy(each, myLocalRepoDir);
    }

    MavenCore.getInstance(myProject).getState().setLocalRepository(myDataTestFixture.getTestDataPath(myLocalRepoDir));

    MavenIndicesManager.getInstance().doInit(new File(myDir, "MavenIndices"));
    myIndicesManager = MavenProjectIndicesManager.getInstance(myProject);
    myIndicesManager.doInit();
  }

  public void tearDown() throws Exception {
    MavenIndicesManager.getInstance().doShutdown();
  }

  public MavenProjectIndicesManager getIndicesManager() {
    return myIndicesManager;
  }

  public MavenWithDataTestFixture getDataTestFixture() {
    return myDataTestFixture;
  }
}
