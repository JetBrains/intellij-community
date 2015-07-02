/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.BranchInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.actions.OpenTaskDialog;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 18.07.13
 */
@SuppressWarnings("ConstantConditions")
public abstract class TaskBranchesTest extends PlatformTestCase {

  private TaskManagerImpl myTaskManager;
  private ChangeListManagerImpl myChangeListManager;
  private VcsDirtyScopeManagerImpl myDirtyScopeManager;

  public void testVcsTaskHandler() throws Exception {

    List<Repository> repositories = initRepositories("community", "idea");
    Repository repository = repositories.get(0);
    String defaultBranchName = getDefaultBranchName();
    assertEquals(defaultBranchName, repository.getCurrentBranchName());

    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(getProject());
    assertEquals(1, handlers.length);
    VcsTaskHandler handler = handlers[0];

    VcsTaskHandler.TaskInfo defaultInfo = handler.getAllExistingTasks()[0];
    assertEquals(defaultBranchName, defaultInfo.getName());
    assertEquals(2, defaultInfo.getRepositories().size());

    final String first = "first";
    VcsTaskHandler.TaskInfo firstInfo = handler.startNewTask(first);
    assertEquals(first, firstInfo.getName());
    assertEquals(2, firstInfo.getRepositories().size());

    assertEquals(2, getNumberOfBranches(repository));
    assertEquals(first, repository.getCurrentBranchName());

    handler.switchToTask(defaultInfo, null);
    assertEquals(defaultBranchName, repository.getCurrentBranchName());

    final String second = "second";
    VcsTaskHandler.TaskInfo secondInfo = handler.startNewTask(second);
    assertEquals(3, getNumberOfBranches(repository));
    assertEquals(second, repository.getCurrentBranchName());
    handler.switchToTask(firstInfo, null);
    commitChanges(repository);
    handler.closeTask(secondInfo, firstInfo);
    assertEquals(2, getNumberOfBranches(repository));
  }

  public void testTaskManager() throws Exception {
    List<Repository> repositories = initRepositories("community", "idea");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    assertNotNull(defaultTask);
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask));
    String defaultBranchName = getDefaultBranchName();

    assertEquals(4, localTask.getBranches().size());
    assertEquals(2, localTask.getBranches(true).size());
    assertEquals(2, localTask.getBranches(false).size());

    assertEquals(2, defaultTask.getBranches().size());

    myTaskManager.activateTask(defaultTask, false);

    Repository repository = repositories.get(0);
    assertEquals(defaultBranchName, repository.getCurrentBranchName());

    foo = myTaskManager.createLocalTask("foo");
    localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask));
    assertEquals("foo", repository.getCurrentBranchName());
    commitChanges(repository);

    myTaskManager.mergeBranch(localTask);
    assertEquals(defaultBranchName, repository.getCurrentBranchName());
    assertEquals(1, getNumberOfBranches(repository));

    myTaskManager.activateTask(defaultTask, false);
    myTaskManager.activateTask(foo, false);
  }

  private void commitChanges(@NotNull Repository repository) throws IOException, VcsException {
    VirtualFile root = repository.getRoot();
    File file = new File(root.getPath(), "foo.txt");
    assertTrue(file.createNewFile());
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    addFiles(getProject(), root, virtualFile);
    myDirtyScopeManager.fileDirty(virtualFile);
    myChangeListManager.ensureUpToDate(false);
    Change change = myChangeListManager.getChange(virtualFile);
    assertNotNull(change);
    ProjectLevelVcsManager.getInstance(getProject()).getAllActiveVcss()[0].getCheckinEnvironment()
      .commit(Collections.singletonList(change), "foo");
  }

  public void testCommit() throws Exception {
    Repository repository = initRepository("foo");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    final LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask));
    commitChanges(repository);
    myTaskManager.mergeBranch(localTask);

    assertEquals(getDefaultBranchName(), repository.getCurrentBranchName());
    assertEquals(1, getNumberOfBranches(repository));
  }

  public void testOpenTaskDialog() throws Exception {
    initRepository("foo");
    String defaultBranchName = getDefaultBranchName();
    LocalTaskImpl task = myTaskManager.createLocalTask("foo");
    OpenTaskDialog dialog = new OpenTaskDialog(getProject(), task);
    Disposer.register(myTestRootDisposable, dialog.getDisposable());
    dialog.createTask();
    assertEquals("foo", myTaskManager.getActiveTask().getSummary());
    List<BranchInfo> branches = task.getBranches(true);
    assertEquals(1, branches.size());
    assertEquals(defaultBranchName, branches.get(0).name);
    branches = task.getBranches(false);
    assertEquals(1, branches.size());
    assertEquals("foo", branches.get(0).name);
  }

  public void testBranchBloating() throws Exception {
    initRepository("foo");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    assertNotNull(defaultTask);
    assertEquals(0, defaultTask.getBranches().size());
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask));
    assertEquals(2, localTask.getBranches().size());
    assertEquals(1, defaultTask.getBranches().size());

    myTaskManager.activateTask(localTask, false);
    LocalTaskImpl bar = myTaskManager.createLocalTask("bar");
    LocalTask barTask = myTaskManager.activateTask(bar, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(barTask));
    assertEquals(1, defaultTask.getBranches().size());
  }

  public void testCleanupRemovedBranch() throws InterruptedException {
    Repository repository = initRepository("foo");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    assertNotNull(defaultTask);
    assertEquals(0, defaultTask.getBranches().size());
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask));
    assertEquals(2, localTask.getBranches().size());
    assertEquals(1, defaultTask.getBranches().size());

    // let's add non-existing branch
    BranchInfo info = new BranchInfo();
    info.name = "non-existing";
    info.repository = defaultTask.getBranches().get(0).repository;
    defaultTask.addBranch(info);
    assertEquals("foo", repository.getCurrentBranchName());
    myTaskManager.activateTask(defaultTask, false);
    assertEquals(getDefaultBranchName(), repository.getCurrentBranchName());
    // do not re-create "non-existing"
    assertEquals(2, getNumberOfBranches(repository));
  }

  public void _testCurrentTasks() throws Exception {
    initRepositories("foo", "bar");
    VcsTaskHandler handler = VcsTaskHandler.getAllHandlers(getProject())[0];
    VcsTaskHandler.TaskInfo[] tasks = handler.getAllExistingTasks();
    assertEquals(1, tasks.length);
    VcsTaskHandler.TaskInfo defaultTask = tasks[0];
    assertEquals(1, handler.getCurrentTasks().length);

    VcsTaskHandler.TaskInfo task = handler.startNewTask("new");
    assertEquals(2, handler.getAllExistingTasks().length);
    assertEquals(1, handler.getCurrentTasks().length);

    handler.closeTask(task, defaultTask);
    VcsTaskHandler.TaskInfo[] existingTasks = handler.getAllExistingTasks();
    assertEquals(Arrays.asList(existingTasks).toString(), 1, existingTasks.length);
    assertEquals(1, handler.getCurrentTasks().length);
  }

  private List<Repository> initRepositories(String... names) {
    return ContainerUtil.map(names, new Function<String, Repository>() {
      @Override
      public Repository fun(String s) {
        return initRepository(s);
      }
    });
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTaskManager = (TaskManagerImpl)TaskManager.getManager(getProject());
    myChangeListManager = (ChangeListManagerImpl)ChangeListManager.getInstance(getProject());
    myDirtyScopeManager = ((VcsDirtyScopeManagerImpl)VcsDirtyScopeManager.getInstance(getProject()));
  }

  @NotNull
  protected abstract Repository initRepository(@NotNull String name);

  @NotNull
  protected abstract String getDefaultBranchName();

  protected abstract int getNumberOfBranches(@NotNull Repository repository);

  protected abstract void addFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull VirtualFile file) throws VcsException;
}
