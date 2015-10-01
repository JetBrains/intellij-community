/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;

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

    MavenProjectsManager.getInstance(myProject).getGeneralSettings().setLocalRepository(
      myRepositoryHelper.getTestDataPath(myLocalRepoDir));

    getIndicesManager().setTestIndexDir(new File(myDir, "MavenIndices"));
    myIndicesManager = MavenProjectIndicesManager.getInstance(myProject);
    myIndicesManager.doInit();
  }

  public void addToRepository(String relPath) throws IOException {
    myRepositoryHelper.copy(relPath, myLocalRepoDir);
  }

  public void tearDown() throws Exception {
    getIndicesManager().doShutdownInTests();
    MavenServerManager.getInstance().shutdown(true);
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
