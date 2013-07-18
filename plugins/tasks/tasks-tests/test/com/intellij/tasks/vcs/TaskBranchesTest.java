/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;
import git4idea.test.GitTestUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 18.07.13
 */
public class TaskBranchesTest extends TaskManagerTestCase {

  public void testGitTaskHandler() throws Exception {

    List<GitRepository> repositories = initRepositories("community", "idea");
    GitRepository repository = repositories.get(0);

    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(getProject());
    assertEquals(1, handlers.length);
    VcsTaskHandler handler = handlers[0];
    handler.startNewTask("first");
    Collection<GitLocalBranch> localBranches = repository.getBranches().getLocalBranches();
    assertEquals(2, localBranches.size());
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    assertNotNull(currentBranch);
    assertEquals("first", currentBranch.getName());
  }

  private List<GitRepository> initRepositories(String... names) {
    return ContainerUtil.map(names, new Function<String, GitRepository>() {
      @Override
      public GitRepository fun(String s) {
        return initRepository(s);
      }
    });
  }

  private GitRepository initRepository(String name) {
    String tempDirectory = FileUtil.getTempDirectory();
    String root = tempDirectory + "/"  + name;
    assertTrue(new File(root).mkdirs());
    GitRepository repository = GitTestUtil.createRepository(getProject(), root);
    GitBranchesCollection branches = repository.getBranches();
    assertEquals(1, branches.getLocalBranches().size());
    return repository;
  }
}
