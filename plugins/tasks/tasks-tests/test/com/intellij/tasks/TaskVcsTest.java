/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.tasks;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskChangelistSupport;
import com.intellij.util.containers.ContainerUtil;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 3/5/12
 */
public class TaskVcsTest extends TaskManagerTestCase {

  private ChangeListManager myChangeListManager;

  public void testInitialState() {
    assertEquals(1, myManager.getLocalTasks().length);
    final LocalTaskImpl defaultTask = myManager.getLocalTasks()[0];
    assertEquals(defaultTask, myManager.getActiveTask());
    assertTrue(defaultTask.isDefault());

    assertEquals(1, myChangeListManager.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());

    assertEquals(defaultTask, myManager.getAssociatedTask(myChangeListManager.getChangeLists().get(0)));
    assertEquals(defaultTask.getChangeLists().get(0).id, myChangeListManager.getChangeLists().get(0).getId());
    assertEquals(defaultTask.getChangeLists().get(0), new ChangeListInfo(myChangeListManager.getChangeLists().get(0)));
  }

  public void testSwitchingTasks() throws Exception {
    final LocalTaskImpl defaultTask = myManager.getLocalTasks()[0];

    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, false);

    assertEquals(2, myManager.getLocalTasks().length);

    LocalTask localTask = myManager.getActiveTask();
    assertEquals(task, localTask);

    assertEquals(0, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(1, myChangeListManager.getChangeLists().size());
    assertEquals(defaultTask, myManager.getAssociatedTask(myChangeListManager.getChangeLists().get(0)));

    myManager.activateTask(defaultTask, false, false);

    assertEquals(0, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(1, myChangeListManager.getChangeLists().size());
    assertEquals(defaultTask, myManager.getAssociatedTask(myChangeListManager.getChangeLists().get(0)));

    myManager.activateTask(localTask, false, true);

    assertEquals(1, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(2, myChangeListManager.getChangeLists().size());

    LocalChangeList activeChangeList = myChangeListManager.getDefaultChangeList();
    LocalChangeList anotherChangeList = myChangeListManager.getChangeLists().get(1 - myChangeListManager.getChangeLists().indexOf(activeChangeList));

    assertEquals(localTask, myManager.getAssociatedTask(activeChangeList));
    assertEquals(activeChangeList.getName(), "TEST-001 Summary");

    assertEquals(defaultTask, myManager.getAssociatedTask(anotherChangeList));
    assertEquals(anotherChangeList.getName(), "Default");

    myManager.activateTask(defaultTask, false, false);

    assertEquals(1, localTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(2, myChangeListManager.getChangeLists().size());

    activeChangeList = myChangeListManager.getDefaultChangeList();
    anotherChangeList = myChangeListManager.getChangeLists().get(1 - myChangeListManager.getChangeLists().indexOf(activeChangeList));

    assertEquals(defaultTask, myManager.getAssociatedTask(activeChangeList));
    assertEquals(activeChangeList.getName(), "Default");

    assertEquals(localTask, myManager.getAssociatedTask(anotherChangeList));
    assertEquals(anotherChangeList.getName(), "TEST-001 Summary");
  }

  public void testAddOneMoreChangeListViaCreateChangeListAction() throws Exception {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, true);

    LocalTask defaultTask = myManager.findTask("Default");
    assertNotNull(defaultTask);
    myManager.activateTask(defaultTask, false, true);
    assertEquals(defaultTask, myManager.getActiveTask());

    LocalTask anotherTask = myManager.findTask("TEST-001");
    assertNotNull(anotherTask);
    myManager.createChangeList(defaultTask, "Default (1)");

    assertEquals(1, anotherTask.getChangeLists().size());
    assertEquals(2, defaultTask.getChangeLists().size());
    assertEquals(3, myChangeListManager.getChangeLists().size());

    LocalChangeList defaultChangeListActive = myChangeListManager.findChangeList("Default (1)");
    assertNotNull(defaultChangeListActive);
    assertTrue(defaultChangeListActive.isDefault());
    LocalChangeList defaultChangeListInactive = myChangeListManager.findChangeList("Default");
    assertNotNull(defaultChangeListInactive);
    LocalChangeList anotherChangeList = myChangeListManager.findChangeList("TEST-001 Summary");
    assertNotNull(anotherChangeList);

    assertEquals(defaultTask, myManager.getAssociatedTask(defaultChangeListActive));
    assertEquals(defaultChangeListActive.getName(), "Default (1)");

    assertEquals(defaultTask, myManager.getAssociatedTask(defaultChangeListInactive));
    assertEquals(defaultChangeListInactive.getName(), "Default");

    assertEquals(anotherTask, myManager.getAssociatedTask(anotherChangeList));
    assertEquals(anotherChangeList.getName(), "TEST-001 Summary");
  }

  public void testRemoveChangelistViaVcsAction() throws Exception {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, true);

    LocalTask defaultTask = myManager.findTask("Default");
    assertNotNull(defaultTask);
    myManager.activateTask(defaultTask, false, true);
    assertEquals(defaultTask, myManager.getActiveTask());

    LocalTask anotherTask = myManager.findTask("TEST-001");
    assertNotNull(anotherTask);

    LocalChangeList defaultChangeList = myChangeListManager.findChangeList("Default");
    assertNotNull(defaultChangeList);
    LocalChangeList anotherChangeList = myChangeListManager.findChangeList("TEST-001 Summary");
    assertNotNull(anotherChangeList);
    myChangeListManager.removeChangeList(anotherChangeList);
    Thread.sleep(1000);

    assertEquals(0, anotherTask.getChangeLists().size());
    assertEquals(1, defaultTask.getChangeLists().size());
    assertEquals(1, myChangeListManager.getChangeLists().size());

    assertEquals(defaultTask, myManager.getAssociatedTask(defaultChangeList));
    assertEquals(defaultChangeList.getName(), "Default");
  }

  public void testAddOneMoreChangeListViaVcsActionToCurrentTask() throws Exception {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, true);

    LocalTask defaultTask = myManager.findTask("Default");
    assertNotNull(defaultTask);
    myManager.activateTask(defaultTask, false, true);
    assertEquals(defaultTask, myManager.getActiveTask());

    LocalTask anotherTask = myManager.findTask("TEST-001");
    assertNotNull(anotherTask);
    addChangeList("Default (1)", "");

    assertEquals(1, anotherTask.getChangeLists().size());
    assertEquals(2, defaultTask.getChangeLists().size());
    assertEquals(3, myChangeListManager.getChangeLists().size());

    LocalChangeList defaultChangeListActive = myChangeListManager.findChangeList("Default");
    assertNotNull(defaultChangeListActive);
    assertTrue(defaultChangeListActive.isDefault());
    LocalChangeList defaultChangeListInactive = myChangeListManager.findChangeList("Default (1)");
    assertNotNull(defaultChangeListInactive);
    LocalChangeList anotherChangeList = myChangeListManager.findChangeList("TEST-001 Summary");
    assertNotNull(anotherChangeList);

    assertEquals(defaultTask, myManager.getAssociatedTask(defaultChangeListActive));
    assertEquals(defaultChangeListActive.getName(), "Default");

    assertEquals(defaultTask, myManager.getAssociatedTask(defaultChangeListInactive));
    assertEquals(defaultChangeListInactive.getName(), "Default (1)");

    assertEquals(anotherTask, myManager.getAssociatedTask(anotherChangeList));
    assertEquals(anotherChangeList.getName(), "TEST-001 Summary");
  }

  public void testAddOneMoreChangeListViaVcsActionToNewTask() throws InterruptedException {
    myManager.getState().associateWithCurrentTaskForNewChangelist = false;

    addChangeList("New Changelist", "");
    assertEquals(2, myManager.getLocalTasks().length);
    assertEquals(2, myChangeListManager.getChangeLists().size());
    LocalChangeList newChangeList = myChangeListManager.findChangeList("New Changelist");
    assertNotNull(newChangeList);
    LocalTask newTask = myManager.getAssociatedTask(newChangeList);
    assertNotNull(newTask);
    assertEquals(newTask.getSummary(), "New Changelist");

    myManager.getState().associateWithCurrentTaskForNewChangelist = true;
  }

  public void testNotAssociateChangeListWithTask() {
    myManager.getState().associateWithTaskForNewChangelist = false;

    addChangeList("New Changelist", "");
    assertEquals(1, myManager.getLocalTasks().length);
    assertEquals(2, myChangeListManager.getChangeLists().size());
    LocalChangeList newChangeList = myChangeListManager.findChangeList("New Changelist");
    assertNotNull(newChangeList);
    assertNull(myManager.getAssociatedTask(newChangeList));

    myManager.getState().associateWithTaskForNewChangelist = true;
  }

  public void testCreateComment() throws Exception {
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{id} {summary} {number} {project}");
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, true);
    LocalTask localTask = myManager.getActiveTask();
    assertNotNull(localTask);
    assertEquals("TEST-001 Summary 001 TEST", localTask.getChangeLists().get(0).comment);
  }

  public void testSaveContextOnCommitForExistingTask() throws Exception {
    myManager.getState().saveContextOnCommit = true;

    assertEquals(1, myManager.getLocalTasks().length);

    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, true);

    assertEquals(2, myManager.getLocalTasks().length);
    assertEquals(2, myChangeListManager.getChangeLists().size());
    LocalTask localTask = myManager.getActiveTask();
    List<ChangeListInfo> changelists = localTask.getChangeLists();
    ChangeListInfo info = changelists.get(0);
    LocalChangeList changeList = myChangeListManager.getChangeList(info.id);
    assertNotNull(changeList);

    CommitChangeListDialog.commitChanges(getProject(), Collections.<Change>emptyList(), changeList, null, changeList.getName());

    assertEquals(2, myManager.getLocalTasks().length); // no extra task created
    assertEquals(2, myChangeListManager.getChangeLists().size());

    assertEquals(localTask, myManager.getAssociatedTask(changeList)); // association should survive
  }

  public void testSaveContextOnCommit() throws Exception {
    myManager.getState().saveContextOnCommit = true;

    assertEquals(1, myManager.getLocalTasks().length);
    assertEquals(1, myChangeListManager.getChangeLists().size());

    myManager.getState().associateWithTaskForNewChangelist = false;
    LocalChangeList changeList = addChangeList("New Changelist", "");
    myManager.getState().associateWithTaskForNewChangelist = true;

    assertEquals(1, myManager.getLocalTasks().length);
    assertEquals(2, myChangeListManager.getChangeLists().size());

    CommitChangeListDialog.commitChanges(getProject(), Collections.<Change>emptyList(), changeList, null, changeList.getName());

    assertEquals(2, myManager.getLocalTasks().length); // extra task created
    assertEquals(2, myChangeListManager.getChangeLists().size());

    assertTrue(ContainerUtil.exists(myManager.getLocalTasks(), new Condition<LocalTaskImpl>() {
      @Override
      public boolean value(final LocalTaskImpl task) {
        return task.getSummary().equals("New Changelist");
      }
    }));
  }

  public LocalChangeList addChangeList(String title, String comment) {
    final LocalChangeList list = myChangeListManager.addChangeList(title, comment);
    new TaskChangelistSupport(getProject(), myManager).addControls(new JPanel(), null).consume(list);
    return list;
  }

  public void testProjectWithDash() throws Exception {
    LocalTaskImpl task = new LocalTaskImpl("foo-bar-001", "summary") {
      @Override
      public TaskRepository getRepository() {
        return myRepository;
      }
    };
    assertEquals("foo-bar", task.getProject());
    assertEquals("001", task.getNumber());
    String name = myManager.getChangelistName(task);
    assertEquals("foo-bar-001 summary", name);
  }

  public void testIds() throws Exception {
    LocalTaskImpl task = new LocalTaskImpl("", "");
    assertEquals("", task.getNumber());
    assertEquals(null, task.getProject());

    task = new LocalTaskImpl("-", "");
    assertEquals("-", task.getNumber());
    assertEquals(null, task.getProject());

    task = new LocalTaskImpl("foo", "");
    assertEquals("foo", task.getNumber());
    assertEquals(null, task.getProject());

    task = new LocalTaskImpl("112", "");
    assertEquals("112", task.getNumber());
    assertEquals(null, task.getProject());
  }

  private TestRepository myRepository;
  private MockAbstractVcs myVcs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVcs = new MockAbstractVcs(getProject());
    AllVcses.getInstance(getProject()).registerManually(myVcs);
    myChangeListManager = ChangeListManager.getInstance(getProject());
    addChangeList("Default", "");
    final LocalChangeList defaultChangeList = myChangeListManager.findChangeList("Default");
    assertNotNull(defaultChangeList);
    myChangeListManager.setDefaultChangeList(defaultChangeList);
    for (LocalChangeList changeList : myChangeListManager.getChangeLists()) {
      if (!changeList.isDefault()) myChangeListManager.removeChangeList(changeList);
    }


    ProjectLevelVcsManager.getInstance(getProject()).setDirectoryMapping("", myVcs.getName());
    ProjectLevelVcsManager.getInstance(getProject()).hasActiveVcss();
    myRepository = new TestRepository();
    myRepository.setTasks(new Task() {
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
        return new Comment[0];
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
        return false;
      }

      @Override
      public String getIssueUrl() {
        return null;
      }

      @Override
      public TaskRepository getRepository() {
        return myRepository;
      }
    });
    myManager.setRepositories(Collections.singletonList(myRepository));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      AllVcses.getInstance(getProject()).unregisterManually(myVcs);
    }
    finally {
      myVcs = null;
    }
    super.tearDown();
  }

  private void clearChangeLists() {
    ChangeListManagerImpl changeListManager = (ChangeListManagerImpl)ChangeListManager.getInstance(getProject());
    List<LocalChangeList> lists = changeListManager.getChangeListsCopy();
    for (LocalChangeList list : lists) {
      if (!list.isDefault()) {
        changeListManager.removeChangeList(list);
      }
    }
  }
}
