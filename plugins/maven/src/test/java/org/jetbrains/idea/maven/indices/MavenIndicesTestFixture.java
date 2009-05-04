package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.io.IOException;

public class MavenIndicesTestFixture {
  private File myDir;
  private Project myProject;
  private String myLocalRepoDir;
  private String[] myExtraRepoDirs;

  private MavenCustomRepositoryHelper myRepositoryHelper;
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
    myRepositoryHelper = new MavenCustomRepositoryHelper(myDir, ArrayUtil.append(myExtraRepoDirs, myLocalRepoDir));

    for (String each : myExtraRepoDirs) {
      addToRepository(each);
    }

    MavenProjectsManager.getInstance(myProject).getGeneralSettings().setLocalRepository(myRepositoryHelper.getTestDataPath(myLocalRepoDir));

    getIndicesManager().setTestIndexDir(new File(myDir, "MavenIndices"));
    myIndicesManager = MavenProjectIndicesManager.getInstance(myProject);
    myIndicesManager.doInit();
  }

  public void addToRepository(String relPath) throws IOException {
    myRepositoryHelper.copy(relPath, myLocalRepoDir);
  }

  public void tearDown() throws Exception {
    getIndicesManager().doShutdownInTests();
  }

  public MavenIndicesManager getIndicesManager() {
    return MavenIndicesManager.getInstance();
  }

  public MavenProjectIndicesManager getProjectIndicesManager() {
    return myIndicesManager;
  }

  public MavenCustomRepositoryHelper getRepositoryHelper() {
    return myRepositoryHelper;
  }
}
