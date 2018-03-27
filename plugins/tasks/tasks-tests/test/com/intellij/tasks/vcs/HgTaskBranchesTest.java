/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.tasks.vcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ObjectUtils;
import hg4idea.test.HgExecutor;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.touch;
import static hg4idea.test.HgExecutor.hg;
import static org.zmlx.hg4idea.util.HgUtil.DOT_HG;

public class HgTaskBranchesTest extends TaskBranchesTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    HgVcs hgVcs = ObjectUtils.assertNotNull(HgVcs.getInstance(myProject));
    hgVcs.getProjectSettings().setCheckIncomingOutgoing(false);
    hgVcs.getGlobalSettings().setHgExecutable(HgExecutor.getHgExecutable());
  }

  @NotNull
  @Override
  protected Repository initRepository(@NotNull String name) {
    String tempDirectory = FileUtil.getTempDirectory();
    String root = tempDirectory + "/" + name;
    assertTrue(new File(root).mkdirs());
    HgPlatformTest.initRepo(root);
    touch("a.txt");
    hg("add a.txt");
    hg("commit -m another -u abc");
    hg("up -r 0");
    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    HgVcs hgVcs = HgVcs.getInstance(myProject);
    assert hgVcs != null;
    vcsManager.setDirectoryMapping(root, HgVcs.VCS_NAME);
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(root, DOT_HG)));

    HgRepository repository =
      HgUtil.getRepositoryManager(myProject).getRepositoryForRoot(LocalFileSystem.getInstance().findFileByIoFile(new File(root)));
    assertNotNull("Couldn't find repository for root " + root, repository);
    return repository;
  }

  @Override
  protected void createAndCommitChanges(@NotNull Repository repository) {
    cd(repository.getRoot());
    touch("foo.txt");
    hg("add foo.txt");
    hg("commit -m another -u abc");
    repository.update();
  }

  @NotNull
  @Override
  protected String getDefaultBranchName() {
    return "default";
  }

  @Override
  protected int getNumberOfBranches(@NotNull Repository repository) {
    if (!(repository instanceof HgRepository)) return 0;
    return ((HgRepository)repository).getOpenedBranches().size() +
           ((HgRepository)repository).getBookmarks().size();
  }
}

