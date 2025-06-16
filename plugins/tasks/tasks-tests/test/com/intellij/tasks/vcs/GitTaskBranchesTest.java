// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.vcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import git4idea.branch.GitBranchesCollection;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.repo.GitRepository;
import git4idea.test.GitExecutor;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.vcs.Executor.touch;
import static git4idea.test.GitExecutor.cd;
import static git4idea.test.GitExecutor.git;

public class GitTaskBranchesTest extends TaskBranchesTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    GitVcsApplicationSettings.getInstance().setPathToGit(GitExecutor.gitExecutable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      GitVcsApplicationSettings.getInstance().setPathToGit(null);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected Repository initRepository(@NotNull String name) {
    return createRepository(name, getProject());
  }

  @NotNull
  public static Repository createRepository(@NotNull String name, Project project) {
    String tempDirectory;
    try {
      tempDirectory = FileUtil.createTempDirectory("foo", "bar").getPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    String root = tempDirectory + "/" + name;
    assertTrue(new File(root).mkdirs());
    GitRepository repository = GitTestUtil.createRepository(project, root);
    GitBranchesCollection branches = repository.getBranches();
    assertEquals(1, branches.getLocalBranches().size());

    ProjectLevelVcsManager.getInstance(project).getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, repository.getVcs()).setValue(
      VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
    return repository;
  }

  @Override
  protected void createAndCommitChanges(@NotNull Repository repository) {
    cd((GitRepository)repository);
    touch("foo.txt");
    git((GitRepository)repository, "add foo.txt");
    git((GitRepository)repository, "commit -m commit");
    repository.update();
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
}
