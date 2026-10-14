// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.vcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.context.WorkingContextManager;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.EDT;
import git4idea.repo.GitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Future;

import static com.intellij.tasks.vcs.GitTaskBranchesTest.createRepository;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.fileEditorManagerFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class VcsContextTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  @SuppressWarnings("unused")
  private final TestFixture<FileEditorManagerImpl> managerFixture = fileEditorManagerFixture(projectFixture, false);

  @TestDisposable
  private Disposable testDisposable;

  private TaskManagerImpl myTaskManager;

  @BeforeEach
  public void setUp() {
    myTaskManager = (TaskManagerImpl)TaskManager.getManager(getProject());
    WorkingContextManager.getInstance(getProject()).enableUntil(testDisposable);
    WorkingContextManager.getInstance(getProject()).getContextFile().delete();
    WorkingContextManager.getInstance(getProject()).getTaskFile().delete();
  }

  @AfterEach
  public void tearDown() {
    RunAll.runAll(
      () -> ChangeListManagerImpl.getInstanceImpl(getProject()).forceStopInTestMode(),
      () -> ChangeListManagerImpl.getInstanceImpl(getProject()).waitEverythingDoneInTestMode(),
      () -> ProjectLevelVcsManager.getInstance(getProject()).setDirectoryMappings(Collections.emptyList()),
      () -> EditorTestUtil.releaseAllEditors()
    );
  }

  @Test
  public void testBranchWorkspace() throws Exception {
    GitRepository repository = EdtTestUtil.runInEdtAndGet(() -> {
      Future<GitRepository> future =
        ApplicationManager.getApplication().executeOnPooledThread(() -> (GitRepository)createRepository("fooBar", getProject()));
      while (!future.isDone()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      return future.get();
    });

    VirtualFile firstFile = EdtTestUtil.runInEdtAndGet(() -> createFile(repository, "first.txt"));
    VirtualFile secondFile = EdtTestUtil.runInEdtAndGet(() -> createFile(repository, "second.txt"));

    LocalTaskImpl first = EdtTestUtil.runInEdtAndGet(() -> createTask("first"));
    assertEquals("first", first.getBranches(false).get(0).name);
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(getProject());
    try {
      LocalTaskImpl second = EdtTestUtil.runInEdtAndGet(() -> {
        manager.openFile(firstFile, true);

        LocalTaskImpl task = createTask("second");
        manager.openFile(secondFile, true);
        return task;
      });

      VirtualFile[] openFiles = EdtTestUtil.runInEdtAndGet(() -> {
        myTaskManager.activateTask(first, true);
        EDT.dispatchAllInvocationEvents();
        return manager.getOpenFiles();
      });
      assertEquals(1, openFiles.length);
      assertEquals("first.txt", openFiles[0].getName());

      openFiles = EdtTestUtil.runInEdtAndGet(() -> {
        myTaskManager.activateTask(second, true);
        EDT.dispatchAllInvocationEvents();
        return manager.getOpenFiles();
      });
      assertEquals(1, openFiles.length);
      assertEquals("second.txt", openFiles[0].getName());
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> manager.closeAllFiles());
      EditorHistoryManager.getInstance(getProject()).removeAllFiles();
    }
  }

  private static Project getProject() {
    return projectFixture.get();
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
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    return task;
  }
}
