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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class MavenIndicesTestFixture {
  private final Path myDir;
  private final Project myProject;
  private final Disposable myTestRootDisposable;
  private final String myLocalRepoDir;
  private final String[] myExtraRepoDirs;

  private MavenCustomRepositoryHelper myRepositoryHelper;

  public MavenIndicesTestFixture(Path dir, Project project, Disposable testRootDisposable) {
    this(dir, project, testRootDisposable, "local1", "local2");
  }

  public MavenIndicesTestFixture(Path dir, Project project, Disposable testRootDisposable, String localRepoDir, String... extraRepoDirs) {
    myDir = dir;
    myProject = project;
    myTestRootDisposable = testRootDisposable;
    myLocalRepoDir = localRepoDir;
    myExtraRepoDirs = extraRepoDirs;
  }

  public void setUpBeforeImport() throws Exception {
    myRepositoryHelper = new MavenCustomRepositoryHelper(myDir.toFile(), ArrayUtil.append(myExtraRepoDirs, myLocalRepoDir));

    for (String each : myExtraRepoDirs) {
      addToRepository(each);
    }

    MavenProjectsManager.getInstance(myProject).getGeneralSettings().setLocalRepository(
      myRepositoryHelper.getTestDataPath(myLocalRepoDir));
    Registry.get("maven.skip.gav.update.in.unit.test.mode").setValue(false, myTestRootDisposable);
  }

  public void setUp() throws Exception {
    setUpBeforeImport();
    setUpAfterImport();
  }

  public void setUpAfterImport() {
    MavenSystemIndicesManager.getInstance().setTestIndicesDir(myDir.resolve("MavenIndices"));
    //todo: rewrite al this to coroutines
    CompletableFuture<Void> f = new CompletableFuture<>();
    getIndicesManager().scheduleUpdateIndicesList(() -> {
      f.complete(null);
      return null;
    });
    f.join();
    getIndicesManager().waitForGavUpdateCompleted();
    UIUtil.dispatchAllInvocationEvents();
  }

  public void addToRepository(String relPath) throws IOException {
    myRepositoryHelper.copy(relPath, myLocalRepoDir);
  }

  public void tearDown() {
    MavenSystemIndicesManager.getInstance().gc();
    MavenServerManager.getInstance().closeAllConnectorsAndWait();
    Disposer.dispose(getIndicesManager());
  }

  public MavenIndicesManager getIndicesManager() {
    return MavenIndicesManager.getInstance(myProject);
  }

  public MavenArchetypeManager getArchetypeManager() {
    return MavenArchetypeManager.getInstance(myProject);
  }

  public MavenCustomRepositoryHelper getRepositoryHelper() {
    return myRepositoryHelper;
  }
}
