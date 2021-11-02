/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class MavenIndicesTestFixture {
  private final Path myDir;
  private final Project myProject;
  private final String myLocalRepoDir;
  private final String[] myExtraRepoDirs;

  private MavenCustomRepositoryHelper myRepositoryHelper;

  public MavenIndicesTestFixture(Path dir, Project project) {
    this(dir, project, "local1", "local2");
  }

  public MavenIndicesTestFixture(File dir, Project project) {
    this(dir.toPath(), project);
  }

  public MavenIndicesTestFixture(Path dir, Project project, String localRepoDir, String... extraRepoDirs) {
    myDir = dir;
    myProject = project;
    myLocalRepoDir = localRepoDir;
    myExtraRepoDirs = extraRepoDirs;
  }

  public void setUp() throws Exception {
    myRepositoryHelper = new MavenCustomRepositoryHelper(myDir.toFile(), ArrayUtil.append(myExtraRepoDirs, myLocalRepoDir));

    for (String each : myExtraRepoDirs) {
      addToRepository(each);
    }

    MavenProjectsManager.getInstance(myProject).getGeneralSettings().setLocalRepository(
      myRepositoryHelper.getTestDataPath(myLocalRepoDir));

    getIndicesManager().setTestIndexDir(myDir.resolve("MavenIndices"));
    getIndicesManager().scheduleUpdateIndicesList(null);
  }

  public void addToRepository(String relPath) throws IOException {
    myRepositoryHelper.copy(relPath, myLocalRepoDir);
  }

  public void tearDown() {
    MavenServerManager.getInstance().shutdown(true);
    Disposer.dispose(getIndicesManager());
  }

  public MavenIndicesManager getIndicesManager() {
    return MavenIndicesManager.getInstance(myProject);
  }

  public MavenCustomRepositoryHelper getRepositoryHelper() {
    return myRepositoryHelper;
  }
}
