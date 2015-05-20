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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.branch.GitBranchesCollection;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.test.GitExecutor;
import git4idea.test.GitTestUtil;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GitTaskBranchesTest extends TaskBranchesTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    GitVcsSettings.getInstance(myProject).getAppSettings().setPathToGit(GitExecutor.PathHolder.GIT_EXECUTABLE);
  }

  @NotNull
  @Override
  protected Repository initRepository(@NotNull String name) {
    String tempDirectory = FileUtil.getTempDirectory();
    String root = tempDirectory + "/" + name;
    assertTrue(new File(root).mkdirs());
    GitRepository repository = GitTestUtil.createRepository(getProject(), root);
    GitBranchesCollection branches = repository.getBranches();
    assertEquals(1, branches.getLocalBranches().size());
    return repository;
  }

  @NotNull
  @Override
  protected String getDefaultBranchName() {
    return "master";
  }

  @Override
  protected int getNumberOfBranches(@NotNull Repository repository) {
    return ((GitRepository)repository).getBranches().getLocalBranches().size();
  }

  @Override
  protected void addFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull VirtualFile file) throws VcsException {
    GitFileUtils.addFiles(project, root, file);
  }
}
