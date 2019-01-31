/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.ui.SingleChangeListCommitter;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.*;
import com.intellij.tasks.actions.OpenTaskDialog;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskChangelistSupport;
import com.intellij.tasks.impl.TaskCheckinHandlerFactory;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl;
import icons.TasksIcons;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class TaskVcsTest extends CodeInsightFixtureTestCase {
  private TestRepository myRepository;
  private MockAbstractVcs myVcs;
  private MyMockChangeProvider myChangeProvider;

  private ChangeListManagerImpl myChangeListManager;
  private TaskManagerImpl myTaskManager;

  public void testInitialState() {
    assertEquals(1, myTaskManager.getLocalTasks().size());
    final LocalTask defaultTask = myTaskManager.getLocalTasks().get(0);
    assertEquals(defaultTask, myTaskManager.getActiveTask());
    assertTrue(defaultTask.isDefault());

    assertEquals(1, myChangeListManager.getChangeListsCopy().size());
    assertEquals(1, defaultTask.getChangeLists().size());

    assertEquals(defaultTask, myTaskManager.getAssociatedTask(myChangeListManager.getChangeListsCopy().get(0)));
    assertEquals(defaultTask.getChangeLists().get(0).id, myChangeListManager.getChangeListsCopy().get(0).getId());
    assertEquals(defaultTask.getChangeLists().get(0), new ChangeListInfo(myChangeListManager.getChangeListsCopy().get(0)));
  }

  public void testSwitchingTasks() {
    final LocalTask defaultTask = myTaskManager.getLocalTasks().get(0);

    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    runOpenTaskDialog(task);

    assertEquals(2, myTaskManager.getLocalTasks().size());

    LocalTask localTask = myTaskManager.getActiveTask();
    assertEquals(task, localTask);

    assertEquals(1, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());
    assertEquals(localTask, myTaskManager.getAssociatedTask(myChangeListManager.getChangeListsCopy().get(0)));

    myTaskManager.activateTask(defaultTask, false);

    assertEquals(1, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());
    assertEquals(defaultTask, myTaskManager.getAssociatedTask(myChangeListManager.getChangeListsCopy().get(0)));

    myTaskManager.activateTask(localTask, false);

    assertEquals(1, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());

    LocalChangeList activeChangeList = myChangeListManager.getDefaultChangeList();
    LocalChangeList anotherChangeList = myChangeListManager.getChangeListsCopy().get(1 - myChangeListManager.getChangeListsCopy().indexOf(activeChangeList));

    assertNotNull(activeChangeList);
    assertEquals(localTask, myTaskManager.getAssociatedTask(activeChangeList));
    assertEquals("TEST-001 Summary", activeChangeList.getName());

    assertEquals(defaultTask, myTaskManager.getAssociatedTask(anotherChangeList));
    assertEquals(LocalChangeList.DEFAULT_NAME, anotherChangeList.getName());

    myTaskManager.activateTask(defaultTask, false);
    myChangeListManager.waitUntilRefreshed();

    assertEquals(1, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());

    activeChangeList = myChangeListManager.getDefaultChangeList();
    anotherChangeList = myChangeListManager.getChangeListsCopy().get(1 - myChangeListManager.getChangeListsCopy().indexOf(activeChangeList));

    assertNotNull(activeChangeList);
    assertEquals(defaultTask, myTaskManager.getAssociatedTask(activeChangeList));
    assertEquals(LocalChangeList.DEFAULT_NAME, activeChangeList.getName());

    assertEquals(localTask, myTaskManager.getAssociatedTask(anotherChangeList));
    assertEquals("TEST-001 Summary", anotherChangeList.getName());
  }

  public void testAddChangeListViaCreateChangeListAction() {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    runOpenTaskDialog(task);
    myChangeListManager.waitUntilRefreshed();

    LocalTask defaultTask = myTaskManager.findTask(LocalTaskImpl.DEFAULT_TASK_ID);
    assertNotNull(defaultTask);
    myTaskManager.activateTask(defaultTask, false);
    myChangeListManager.waitUntilRefreshed();
    assertEquals(defaultTask, myTaskManager.getActiveTask());

    LocalTask anotherTask = myTaskManager.findTask("TEST-001");
    assertNotNull(anotherTask);
    myTaskManager.createChangeList(defaultTask, "Default (1)");
    myChangeListManager.waitUntilRefreshed();

    assertEquals(1, anotherTask.getChangeLists().size());
    assertEquals(2, defaultTask.getChangeLists().size());
    assertEquals(3, myChangeListManager.getChangeListsCopy().size());

    LocalChangeList defaultChangeListActive = myChangeListManager.findChangeList("Default (1)");
    assertNotNull(defaultChangeListActive);
    assertTrue(defaultChangeListActive.isDefault());
    LocalChangeList defaultChangeListInactive = myChangeListManager.findChangeList(LocalChangeList.DEFAULT_NAME);
    assertNotNull(defaultChangeListInactive);
    LocalChangeList anotherChangeList = myChangeListManager.findChangeList("TEST-001 Summary");
    assertNotNull(anotherChangeList);

    assertEquals(defaultTask, myTaskManager.getAssociatedTask(defaultChangeListActive));
    assertEquals("Default (1)", defaultChangeListActive.getName());

    assertEquals(defaultTask, myTaskManager.getAssociatedTask(defaultChangeListInactive));
    assertEquals(LocalChangeList.DEFAULT_NAME, defaultChangeListInactive.getName());

    assertEquals(anotherTask, myTaskManager.getAssociatedTask(anotherChangeList));
    assertEquals("TEST-001 Summary", anotherChangeList.getName());
  }

  public void testRemoveChangelistViaVcsAction() {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    runOpenTaskDialog(task);
    myChangeListManager.waitUntilRefreshed();

    LocalTask defaultTask = myTaskManager.findTask(LocalTaskImpl.DEFAULT_TASK_ID);
    assertNotNull(defaultTask);
    myTaskManager.activateTask(defaultTask, false);
    myChangeListManager.waitUntilRefreshed();
    assertEquals(defaultTask, myTaskManager.getActiveTask());

    LocalTask anotherTask = myTaskManager.findTask("TEST-001");
    assertNotNull(anotherTask);

    LocalChangeList defaultChangeList = myChangeListManager.findChangeList(LocalChangeList.DEFAULT_NAME);
    assertNotNull(defaultChangeList);
    LocalChangeList anotherChangeList = myChangeListManager.findChangeList("TEST-001 Summary");
    assertNotNull(anotherChangeList);
    removeChangeList(anotherChangeList);

    assertEquals(1, anotherTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(1, myChangeListManager.getChangeListsCopy().size());

    assertEquals(defaultTask, myTaskManager.getAssociatedTask(defaultChangeList));
    assertEquals(LocalChangeList.DEFAULT_NAME, defaultChangeList.getName());
  }

  public void testAddChangeListViaVcsAction() {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    runOpenTaskDialog(task);
    myChangeListManager.waitUntilRefreshed();

    LocalTask defaultTask = myTaskManager.findTask(LocalTaskImpl.DEFAULT_TASK_ID);
    assertNotNull(defaultTask);
    myTaskManager.activateTask(defaultTask, false);
    myChangeListManager.waitUntilRefreshed();
    assertEquals(defaultTask, myTaskManager.getActiveTask());

    LocalTask anotherTask = myTaskManager.findTask("TEST-001");
    assertNotNull(anotherTask);
    addChangeList("Default (1)");

    assertEquals(1, anotherTask.getChangeLists().size());
    assertEquals(2, defaultTask.getChangeLists().size());
    assertEquals(3, myChangeListManager.getChangeListsCopy().size());

    LocalChangeList defaultChangeListActive = myChangeListManager.findChangeList(LocalChangeList.DEFAULT_NAME);
    assertNotNull(defaultChangeListActive);
    assertTrue(myChangeListManager.getDefaultListName(), defaultChangeListActive.isDefault());

    LocalChangeList defaultChangeListInactive = myChangeListManager.findChangeList("Default (1)");
    assertNotNull(defaultChangeListInactive);
    LocalChangeList anotherChangeList = myChangeListManager.findChangeList("TEST-001 Summary");
    assertNotNull(anotherChangeList);

    assertEquals(defaultTask, myTaskManager.getAssociatedTask(defaultChangeListActive));
    assertEquals(LocalChangeList.DEFAULT_NAME, defaultChangeListActive.getName());

    assertEquals(defaultTask, myTaskManager.getAssociatedTask(defaultChangeListInactive));
    assertEquals("Default (1)", defaultChangeListInactive.getName());

    assertEquals(anotherTask, myTaskManager.getAssociatedTask(anotherChangeList));
    assertEquals("TEST-001 Summary", anotherChangeList.getName());
  }

  public void testTrackContext() {
    myTaskManager.getState().trackContextForNewChangelist = true;

    addChangeList("New Changelist");
    assertEquals(2, myTaskManager.getLocalTasks().size());
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());
    LocalChangeList newChangeList = myChangeListManager.findChangeList("New Changelist");
    assertNotNull(newChangeList);
    LocalTask newTask = myTaskManager.getAssociatedTask(newChangeList);
    assertNotNull(newTask);
    assertEquals("New Changelist", newTask.getSummary());

    myTaskManager.getState().trackContextForNewChangelist = false;
  }

  public void testCreateComment() {
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{id} {summary} {number} {project}");
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    runOpenTaskDialog(task);
    myChangeListManager.waitUntilRefreshed();
    LocalTask localTask = myTaskManager.getActiveTask();
    assertNotNull(localTask);
    assertEquals("TEST-001", localTask.getId());
    List<ChangeListInfo> lists = localTask.getChangeLists();
    assertEquals(1, lists.size());
    assertEquals("TEST-001 Summary 001 TEST", lists.get(0).comment);
  }

  public void testSaveContextOnCommitForExistingTask() {
    myTaskManager.getState().saveContextOnCommit = true;

    assertEquals(1, myTaskManager.getLocalTasks().size());

    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    assertEquals(1, myChangeListManager.getChangeListsCopy().size());  // default change list should be here
    runOpenTaskDialog(task);
    myChangeListManager.waitUntilRefreshed();

    assertEquals(2, myTaskManager.getLocalTasks().size());
    List<LocalChangeList> copy = myChangeListManager.getChangeListsCopy();
    assertEquals(copy.toString(), 2, copy.size());
    LocalTask localTask = myTaskManager.getActiveTask();
    List<ChangeListInfo> changelists = localTask.getChangeLists();
    ChangeListInfo info = changelists.get(0);
    LocalChangeList changeList = myChangeListManager.getChangeList(info.id);
    assertNotNull(changeList);

    List<Change> changes = addChanges(changeList);
    commitChanges(changeList, changes);

    assertEquals(2, myTaskManager.getLocalTasks().size()); // no extra task created
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());

    assertEquals(localTask, myTaskManager.getAssociatedTask(changeList)); // association should survive
  }

  public void testSaveContextOnCommit() {
    myTaskManager.getState().saveContextOnCommit = true;

    assertEquals(1, myTaskManager.getLocalTasks().size());
    assertEquals(1, myChangeListManager.getChangeListsCopy().size());

    LocalChangeList changeList = addChangeList("New Changelist");

    assertEquals(1, myTaskManager.getLocalTasks().size());
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());

    List<Change> changes = addChanges(changeList);
    commitChanges(changeList, changes);

    assertEquals(2, myTaskManager.getLocalTasks().size()); // extra task created
    assertEquals(2, myChangeListManager.getChangeListsCopy().size());

    assertTrue(ContainerUtil.exists(myTaskManager.getLocalTasks(), task -> task.getSummary().equals("New Changelist")));
  }

  private void commitChanges(LocalChangeList changeList, List<Change> changes) {
    String commitMessage = changeList.getName();

    CheckinProjectPanel panel = EasyMock.createMock(CheckinProjectPanel.class);
    EasyMock.expect(panel.getProject()).andReturn(getProject());
    EasyMock.expect(panel.getCommitMessage()).andReturn(commitMessage);
    EasyMock.replay(panel);

    CheckinHandler checkinHandler = new TaskCheckinHandlerFactory().createHandler(panel, new CommitContext());

    List<CheckinHandler> handlers = ContainerUtil.list(checkinHandler);
    SingleChangeListCommitter committer =
      new SingleChangeListCommitter(getProject(), changeList, changes, commitMessage, handlers, FunctionUtil.nullConstant(), null, "Commit",
                                    false);

    committer.runCommit("Commit", true);
  }

  private LocalChangeList addChangeList(String title) {
    final LocalChangeList list = myChangeListManager.addChangeList(title, "");
    new TaskChangelistSupport(getProject(), myTaskManager).addControls(new JPanel(), null).consume(list);
    return list;
  }

  private void removeChangeList(LocalChangeList changeList) {
    myChangeListManager.removeChangeList(changeList);
    myTaskManager.getChangeListListener().changeListRemoved(changeList);
  }

  @NotNull
  private List<Change> addChanges(@NotNull LocalChangeList list) {
    VirtualFile file = myFixture.getTempDirFixture().createFile("Test.txt");
    FilePath path = VcsUtil.getFilePath(file);
    Change change = new Change(null,
                               new CurrentContentRevision(path));

    List<Change> changes = Collections.singletonList(change);
    myChangeProvider.setChanges(changes);

    VcsDirtyScopeManager.getInstance(getProject()).markEverythingDirty();
    myChangeListManager.scheduleUpdate();
    myChangeListManager.waitUntilRefreshed();

    myChangeListManager.moveChangesTo(list, change);
    myChangeListManager.waitUntilRefreshed();

    LOG.debug(dumpChangeListManager());

    return changes;
  }

  public void testProjectWithDash() {
    LocalTaskImpl task = new LocalTaskImpl("foo-bar-001", "summary") {
      @Override
      public TaskRepository getRepository() {
        return myRepository;
      }

      @Override
      public boolean isIssue() {
        return true;
      }
    };
    assertEquals("foo-bar", task.getProject());
    assertEquals("001", task.getNumber());
    String name = myTaskManager.getChangelistName(task);
    assertEquals("foo-bar-001 summary", name);
  }

  public void testIds() {
    LocalTaskImpl task = new LocalTaskImpl("", "");
    assertEquals("", task.getNumber());
    assertNull(task.getProject());

    task = new LocalTaskImpl("-", "");
    assertEquals("-", task.getNumber());
    assertNull(task.getProject());

    task = new LocalTaskImpl("foo", "");
    assertEquals("foo", task.getNumber());
    assertNull(task.getProject());

    task = new LocalTaskImpl("112", "");
    assertEquals("112", task.getNumber());
    assertNull(task.getProject());
  }

  public void testRestoreChangelist() {
    final LocalTaskImpl task = new LocalTaskImpl("foo", "bar");
    runOpenTaskDialog(task);
    runOpenTaskDialog(new LocalTaskImpl("next", ""));

    myChangeListManager.ensureUpToDate();
    final String changelistName = myTaskManager.getChangelistName(task);
    myChangeListManager.removeChangeList(changelistName);

    myChangeListManager.waitUntilRefreshed();
    assertTrue(myTaskManager.isLocallyClosed(task));
    myTaskManager.activateTask(task, false);
    assertNotNull(myChangeListManager.findChangeList(changelistName));
  }

  public void testSuggestBranchName() {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    assertTrue(task.isIssue());
    assertEquals("TEST-001", myTaskManager.suggestBranchName(task));

    LocalTaskImpl simple = new LocalTaskImpl("1", "simple");
    assertEquals("simple", myTaskManager.suggestBranchName(simple));

    LocalTaskImpl strange = new LocalTaskImpl("1", "very long and strange summary");
    assertEquals("very-long", myTaskManager.suggestBranchName(strange));

    myTaskManager.getState().branchNameFormat = "{id} {summary}";

    LocalTaskImpl withIllegalSymbolsInIssue = new LocalTaskImpl("1", "contains Illegal$Symbols");
    withIllegalSymbolsInIssue.setIssue(true);
    assertEquals("1-contains-Illegal$Symbols", myTaskManager.suggestBranchName(withIllegalSymbolsInIssue));
  }

  public void testShelveChanges() {

    LocalTask activeTask = myTaskManager.getActiveTask();
    addChanges(myChangeListManager.getDefaultChangeList());

    myTaskManager.getState().shelveChanges = true;
    LocalTaskImpl task = new LocalTaskImpl("id", "summary");
    runOpenTaskDialog(task);

    assertEquals(dumpChangeListManager(), activeTask.getSummary(), activeTask.getShelfName());

    List<ShelvedChangeList> lists = ShelveChangesManager.getInstance(getProject()).getShelvedChangeLists();
    assertTrue(lists.stream().anyMatch(list -> list.DESCRIPTION.equals(activeTask.getShelfName())));

    assertEmpty(myChangeListManager.getDefaultChangeList().getChanges());
    myTaskManager.activateTask(activeTask, true);
    Collection<Change> changes = myChangeListManager.getDefaultChangeList().getChanges();
    assertNotEmpty(changes);
  }

  public void testAssociatedChangelist() {
    LocalChangeList changeList = myChangeListManager.getDefaultChangeList();
    assertNotNull(changeList);
    assertEquals(myTaskManager.getActiveTask(), myTaskManager.getAssociatedTask(changeList));
    LocalTaskImpl bond = new LocalTaskImpl("007", "Bond");
    TestRepository repository = new TestRepository();
    repository.setShouldFormatCommitMessage(true);
    bond.setRepository(repository);

    myTaskManager.getState().createChangelist = false;
    runOpenTaskDialog(bond);
    assertEquals(1, bond.getChangeLists().size());
    assertEquals(changeList.getId(), bond.getChangeLists().get(0).id);
    assertEquals("007 Bond", myChangeListManager.getDefaultChangeList().getComment());
  }

  public void testOpenTask() {
    LocalTaskImpl task = new LocalTaskImpl("id", "summary");
    runOpenTaskDialog(task);
    List<ChangeListInfo> lists = task.getChangeLists();
    assertEquals(1, lists.size());
  }

  private void runOpenTaskDialog(Task task) {
    OpenTaskDialog dialog = new OpenTaskDialog(getProject(), task);
    try {
      dialog.createTask();
    }
    finally {
      dialog.close(DialogWrapper.OK_EXIT_CODE);
    }
    UIUtil.dispatchAllInvocationEvents();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myVcs = new MockAbstractVcs(getProject());
    myChangeProvider = new MyMockChangeProvider();
    myVcs.setChangeProvider(myChangeProvider);
    myChangeListManager = (ChangeListManagerImpl)ChangeListManager.getInstance(getProject());

    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(getProject());
    vcsManager.registerVcs(myVcs);
    vcsManager.setDirectoryMappings(Collections.singletonList(new VcsDirectoryMapping("", myVcs.getName())));
    vcsManager.waitForInitialized();
    assertTrue(vcsManager.hasActiveVcss());

    myTaskManager = (TaskManagerImpl)TaskManager.getManager(getProject());
    myRepository = new TestRepository();
    myRepository.setTasks(new MyTask());
    myTaskManager.setRepositories(Collections.singletonList(myRepository));
  }

  @Override
  protected void tearDown() throws Exception {
    new RunAll()
      .append(() -> AsyncVfsEventsPostProcessorImpl.waitEventsProcessed())
      .append(() -> myTaskManager.setRepositories(Collections.emptyList()))
      .append(() -> AllVcses.getInstance(getProject()).unregisterManually(myVcs))
      .append(() -> {
        myTaskManager = null;
        myVcs = null;
        myChangeListManager = null;
      })
      .append(() -> super.tearDown())
      .run();
  }

  @NotNull
  private String dumpChangeListManager() {
    return StringUtil.join(myChangeListManager.getChangeLists(), list -> String
      .format("list: %s (%s) changes: %s", list.getName(), list.getId(), StringUtil.join(list.getChanges(), ", ")), "\n");
  }

  private static class MyMockChangeProvider implements ChangeProvider {
    private List<Change> myChanges = Collections.emptyList();

    public void setChanges(List<Change> changes) {
      myChanges = changes;
    }

    @Override
    public void getChanges(@NotNull VcsDirtyScope dirtyScope,
                           @NotNull final ChangelistBuilder builder,
                           @NotNull ProgressIndicator progress,
                           @NotNull ChangeListManagerGate addGate) {
      for (Change change : myChanges) {
        builder.processChange(change, MockAbstractVcs.getKey());
      }
    }

    @Override
    public boolean isModifiedDocumentTrackingRequired() {
      return false;
    }

    @Override
    public void doCleanup(List<VirtualFile> files) {
    }
  }

  private class MyTask extends Task {
    @NotNull
    @Override
    public String getId() {
      return "TEST-001";
    }

    @NotNull
    @Override
    public String getSummary() {
      return "Summary";
    }

    @Override
    public String getDescription() {
      return null;
    }

    @NotNull
    @Override
    public Comment[] getComments() {
      return Comment.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      return TasksIcons.Unknown;
    }

    @NotNull
    @Override
    public TaskType getType() {
      return TaskType.BUG;
    }

    @Override
    public Date getUpdated() {
      return null;
    }

    @Override
    public Date getCreated() {
      return null;
    }

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public boolean isIssue() {
      return true;
    }

    @Override
    public String getIssueUrl() {
      return null;
    }

    @Override
    public TaskRepository getRepository() {
      return myRepository;
    }
  }
}
