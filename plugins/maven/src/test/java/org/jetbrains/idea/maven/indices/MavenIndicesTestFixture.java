package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.core.MavenCore;

import java.io.File;

public class MavenIndicesTestFixture {
  private File myDir;
  private Project myProject;
  private String myLocalRepoDir;
  private String[] myExtraRepoDirs;

  private MavenWithDataTestFixture myDataTestFixture;
  private MavenIndicesManager myIndicesManager;

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
      FileUtil.copyDir(new File(myDataTestFixture.getTestDataPath(each)),
                       new File(myDataTestFixture.getTestDataPath(myLocalRepoDir)));
    }

    MavenCore.getInstance(myProject).getState().setLocalRepository(myDataTestFixture.getTestDataPath(myLocalRepoDir));

    myIndicesManager = MavenIndicesManager.getInstance();
    myIndicesManager.doInit(new File(myDir, "MavenIndices"));
    myIndicesManager.initProjectIndicesOnActivation(myProject);
  }

  public void tearDown() throws Exception {
    myIndicesManager.doShutdown();
  }

  public MavenIndicesManager getIndicesManager() {
    return myIndicesManager;
  }

  public MavenWithDataTestFixture getDataTestFixture() {
    return myDataTestFixture;
  }
}
