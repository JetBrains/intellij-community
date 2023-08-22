// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.vcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.tasks.BranchInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.actions.OpenTaskDialog;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.RunAll;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskBranchesTest extends HeavyPlatformTestCase {

  private TaskManagerImpl myTaskManager;

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected void tearDown() throws Exception {
    new RunAll(
      () -> ChangeListManagerImpl.getInstanceImpl(myProject).forceStopInTestMode(),
      () -> ChangeListManagerImpl.getInstanceImpl(myProject).waitEverythingDoneInTestMode(),
      () -> myTaskManager = null,
      () -> ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      })
    ).run();
  }

  public void testVcsTaskHandler() {

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
    repository.update();
    assertEquals(first, firstInfo.getName());
    assertEquals(2, firstInfo.getRepositories().size());

    assertEquals(2, getNumberOfBranches(repository));
    assertEquals(first, repository.getCurrentBranchName());

    ApplicationManager.getApplication().invokeAndWait(() -> handler.switchToTask(defaultInfo, null));
    repository.update();
    assertEquals(defaultBranchName, repository.getCurrentBranchName());

    final String second = "second";
    VcsTaskHandler.TaskInfo secondInfo = handler.startNewTask(second);
    repository.update();
    assertEquals(3, getNumberOfBranches(repository));
    assertEquals(second, repository.getCurrentBranchName());
    ApplicationManager.getApplication().invokeAndWait(() -> handler.switchToTask(firstInfo, null));
    createAndCommitChanges(repository);
    ApplicationManager.getApplication().invokeAndWait(() -> handler.closeTask(secondInfo, firstInfo));
    repository.update();
    assertEquals(2, getNumberOfBranches(repository));
  }

  public void testTaskManager() {
    List<Repository> repositories = initRepositories("community", "idea");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    assertNotNull(defaultTask);
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask), null);
    String defaultBranchName = getDefaultBranchName();

    assertEquals(4, localTask.getBranches().size());
    assertEquals(2, localTask.getBranches(true).size());
    assertEquals(2, localTask.getBranches(false).size());

    assertEquals(2, defaultTask.getBranches().size());

    ApplicationManager.getApplication().invokeAndWait(() -> myTaskManager.activateTask(defaultTask, false));

    Repository repository = repositories.get(0);
    assertEquals(defaultBranchName, repository.getCurrentBranchName());

    LocalTaskImpl foo2 = myTaskManager.createLocalTask("foo");
    LocalTask localTask2 = myTaskManager.activateTask(foo2, false);
    ApplicationManager.getApplication()
      .invokeAndWait(() -> myTaskManager.createBranch(localTask2, defaultTask, myTaskManager.suggestBranchName(localTask2), null));
    assertEquals("foo", repository.getCurrentBranchName());
    createAndCommitChanges(repository);

    ApplicationManager.getApplication().invokeAndWait(() -> myTaskManager.mergeBranch(localTask2));
    repository.update();
    assertEquals(defaultBranchName, repository.getCurrentBranchName());
    assertEquals(1, getNumberOfBranches(repository));

    ApplicationManager.getApplication().invokeAndWait(() -> myTaskManager.activateTask(defaultTask, false));
    ApplicationManager.getApplication().invokeAndWait(() -> myTaskManager.activateTask(foo2, false));
  }

  public void testCommit() {
    Repository repository = initRepository("foo");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    final LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask), null);
    createAndCommitChanges(repository);
    ApplicationManager.getApplication().invokeAndWait(() -> myTaskManager.mergeBranch(localTask));
    repository.update();
    assertEquals(getDefaultBranchName(), repository.getCurrentBranchName());
    assertEquals(1, getNumberOfBranches(repository));
  }

  public void testOpenTaskDialog() {
    initRepository("foo");
    String defaultBranchName = getDefaultBranchName();
    LocalTaskImpl task = myTaskManager.createLocalTask("foo");
    ApplicationManager.getApplication().invokeAndWait(() -> {
      OpenTaskDialog dialog = new OpenTaskDialog(getProject(), task);
      Disposer.register(getTestRootDisposable(), dialog.getDisposable());
      try {
        dialog.createTask();
        assertEquals("foo", myTaskManager.getActiveTask().getSummary());
        List<BranchInfo> branches = task.getBranches(true);
        assertEquals(1, branches.size());
        assertEquals(defaultBranchName, branches.get(0).name);
        branches = task.getBranches(false);
        assertEquals(1, branches.size());
        assertEquals("foo", branches.get(0).name);
      }
      finally {
        dialog.close(DialogWrapper.OK_EXIT_CODE);
      }
      UIUtil.dispatchAllInvocationEvents();
    });
  }

  public void testBranchBloating() {
    Repository repository = initRepository("foo");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    assertNotNull(defaultTask);
    assertEquals(0, defaultTask.getBranches().size());
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask), null);
    repository.update();
    assertEquals(2, localTask.getBranches().size());
    assertEquals(1, defaultTask.getBranches().size());

    myTaskManager.activateTask(localTask, false);
    LocalTaskImpl bar = myTaskManager.createLocalTask("bar");
    LocalTask barTask = myTaskManager.activateTask(bar, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(barTask), null);
    repository.update();
    assertEquals(1, defaultTask.getBranches().size());
  }

  public void testCleanupRemovedBranch() {
    Repository repository = initRepository("foo");
    LocalTask defaultTask = myTaskManager.getActiveTask();
    assertNotNull(defaultTask);
    assertEquals(0, defaultTask.getBranches().size());
    LocalTaskImpl foo = myTaskManager.createLocalTask("foo");
    LocalTask localTask = myTaskManager.activateTask(foo, false);
    myTaskManager.createBranch(localTask, defaultTask, myTaskManager.suggestBranchName(localTask), null);
    assertEquals(2, localTask.getBranches().size());
    assertEquals(1, defaultTask.getBranches().size());

    // let's add non-existing branch
    BranchInfo info = new BranchInfo();
    info.name = "non-existing";
    info.repository = defaultTask.getBranches().get(0).repository;
    defaultTask.addBranch(info);
    repository.update();
    assertEquals("foo", repository.getCurrentBranchName());
    ApplicationManager.getApplication().invokeAndWait(() -> myTaskManager.activateTask(defaultTask, false));
    repository.update();
    assertEquals(getDefaultBranchName(), repository.getCurrentBranchName());
    // do not re-create "non-existing"
    assertEquals(2, getNumberOfBranches(repository));
  }

  public void testSuggestBranchName() {
    initRepositories("community", "idea");
    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(getProject());
    assertEquals(1, handlers.length);
    VcsTaskHandler handler = handlers[0];
    String startName = "-Hello, this is long name with : and $";
    assertFalse(handler.isBranchNameValid(startName));
    String cleanUpBranchName = handler.cleanUpBranchName(startName);
    assertTrue(handler.isBranchNameValid(cleanUpBranchName));
  }

  public void _testCurrentTasks() {
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
    return ContainerUtil.map(names, this::initRepository);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTaskManager = (TaskManagerImpl)TaskManager.getManager(getProject());
  }

  @NotNull
  protected abstract Repository initRepository(@NotNull String name);

  @NotNull
  protected abstract String getDefaultBranchName();

  protected abstract int getNumberOfBranches(@NotNull Repository repository);

  protected abstract void createAndCommitChanges(@NotNull Repository repository);
}
