// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.vcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.context.WorkingContextManager;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.RunAll;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import git4idea.repo.GitRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Future;

import static com.intellij.tasks.vcs.GitTaskBranchesTest.createRepository;

public class VcsContextTest extends FileEditorManagerTestCase {
  private TaskManagerImpl myTaskManager;

  @Override
  public void setUp() {
    super.setUp();
    myTaskManager = (TaskManagerImpl)TaskManager.getManager(getProject());
    WorkingContextManager.getInstance(getProject()).enableUntil(getTestRootDisposable());
    WorkingContextManager.getInstance(getProject()).getContextFile().delete();
    WorkingContextManager.getInstance(getProject()).getTaskFile().delete();
  }

  @Override
  protected void tearDown() {
    RunAll.runAll(
      () -> ChangeListManagerImpl.getInstanceImpl(getProject()).forceStopInTestMode(),
      () -> ChangeListManagerImpl.getInstanceImpl(getProject()).waitEverythingDoneInTestMode(),
      () -> ProjectLevelVcsManager.getInstance(getProject()).setDirectoryMappings(Collections.emptyList()),
      () -> super.tearDown()
    );
  }

  public void testBranchWorkspace() throws Exception {
    Future<GitRepository> future =
      ApplicationManager.getApplication().executeOnPooledThread(() -> (GitRepository)createRepository("fooBar", getProject()));
    while (!future.isDone()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    GitRepository repository = future.get();

    VirtualFile firstFile = createFile(repository, "first.txt");
    VirtualFile secondFile = createFile(repository, "second.txt");

    LocalTaskImpl first = createTask("first");
    assertEquals("first", first.getBranches(false).get(0).name);
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(getProject());
    try {
      manager.openFile(firstFile, true);

      LocalTaskImpl second = createTask("second");
      manager.openFile(secondFile, true);

      myTaskManager.activateTask(first, true);
      EDT.dispatchAllInvocationEvents();
      assertEquals(1, manager.getOpenFiles().length);
      assertEquals("first.txt", manager.getOpenFiles()[0].getName());

      myTaskManager.activateTask(second, true);
      EDT.dispatchAllInvocationEvents();
      assertEquals(1, manager.getOpenFiles().length);
      assertEquals("second.txt", manager.getOpenFiles()[0].getName());
    }
    finally {
      manager.closeAllFiles();
      EditorHistoryManager.getInstance(getProject()).removeAllFiles();
    }
  }

  private VirtualFile createFile(Repository repository, String name) throws IOException {
    VirtualFile root = repository.getRoot();
    VirtualFile child = root.findChild(name);
    if (child != null) return child;
    return WriteAction.compute(() -> root.createChildData(this, name));
  }

  private LocalTaskImpl createTask(String name) {
    LocalTask defaultTask = myTaskManager.getActiveTask();
    LocalTaskImpl task = myTaskManager.createLocalTask(name);
    final LocalTask localTask = myTaskManager.activateTask(task, true);
    myTaskManager.createBranch(localTask, defaultTask, name, null);
    UIUtil.dispatchAllInvocationEvents();
    return task;
  }
}
