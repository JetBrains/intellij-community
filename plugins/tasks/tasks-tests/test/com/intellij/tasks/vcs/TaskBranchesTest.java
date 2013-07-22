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
import com.intellij.tasks.BranchInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;
import git4idea.test.GitTestUtil;

import java.io.File;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 18.07.13
 */
@SuppressWarnings("ConstantConditions")
public class TaskBranchesTest extends TaskManagerTestCase {

  // todo re-enable
  public void _testGitTaskHandler() throws Exception {

    List<GitRepository> repositories = initRepositories("community", "idea");
    GitRepository repository = repositories.get(0);
    assertEquals("master", repository.getCurrentBranch().getName());

    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(getProject());
    assertEquals(1, handlers.length);
    VcsTaskHandler handler = handlers[0];

    VcsTaskHandler.TaskInfo defaultInfo = handler.getActiveTask();
    final String first = "first";
    VcsTaskHandler.TaskInfo firstInfo = handler.startNewTask(first);
    assertEquals(2, repository.getBranches().getLocalBranches().size());
    assertEquals(first, repository.getCurrentBranch().getName());

    handler.switchToTask(defaultInfo);
    assertEquals("master", repository.getCurrentBranch().getName());

    final String second = "second";
    VcsTaskHandler.TaskInfo secondInfo = handler.startNewTask(second);
    assertEquals(3, repository.getBranches().getLocalBranches().size());
    assertEquals(second, repository.getCurrentBranch().getName());

    handler.switchToTask(firstInfo);
    assertEquals(first, repository.getCurrentBranch().getName());

    handler.closeTask(secondInfo);
    assertEquals(2, repository.getBranches().getLocalBranches().size());
  }

  public void testTaskManager() throws Exception {
    List<GitRepository> repositories = initRepositories("community", "idea");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    assertNotNull(defaultTask);
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.activateInVcs(localTask, defaultTask, TaskManager.VcsOperation.CREATE_BRANCH, myTaskManager.suggestBranchName(localTask));
    List<BranchInfo> branches = localTask.getBranches();
    assertEquals(2, branches.size());
    assertEquals(2, defaultTask.getBranches().size());

    myTaskManager.activateTask(defaultTask, false);
    assertEquals("master", repositories.get(0).getCurrentBranch().getName());

    foo = myTaskManager.createLocalTask("foo");
    localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.activateInVcs(localTask, defaultTask, TaskManager.VcsOperation.CREATE_BRANCH, myTaskManager.suggestBranchName(localTask));
    assertEquals("foo", repositories.get(0).getCurrentBranch().getName());
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
