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

import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.tasks.impl.LocalTaskImpl;
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

  public void testCreateChangelistForLocalTask() throws Exception {
    LocalTaskImpl task = new LocalTaskImpl("TEST-001", "Summary");
    ChangeListInfo info = createChangelist(task);
    assertEquals("Summary", info.name);
  }

  public void testCreateChangelist() throws Exception {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    ChangeListInfo info = createChangelist(task);
    assertEquals("TEST-001 Summary", info.name);
    assertEquals("", info.comment);
  }

  public void testActivateTask() throws Exception {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    List<ChangeListInfo> changelists = myManager.getOpenChangelists(task);
    assertEquals(0, changelists.size());
    myManager.activateTask(task, false, true);
    LocalTask localTask = myManager.getActiveTask();
    changelists = myManager.getOpenChangelists(localTask);
    assertTrue(changelists.size() > 0);
    assertEquals("TEST-001 Summary", changelists.get(0).name);
  }

  public void testCreateComment() throws Exception {
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{id} {summary} {number} {project}");
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    ChangeListInfo info = createChangelist(task);
    assertEquals("TEST-001 Summary 001 TEST", info.comment);
  }

  private ChangeListInfo createChangelist(Task task) {
    clearChangeLists();
    LocalTaskImpl localTask = new LocalTaskImpl(task);
    myManager.createChangeList(localTask, myManager.getChangelistName(task));
    List<ChangeListInfo> list = myManager.getOpenChangelists(localTask);
    assertEquals(1, list.size());
    ChangeListInfo info = list.get(0);
    list.clear();
    return info;
  }

  private TestRepository myRepository;
  private MockAbstractVcs myVcs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVcs = new MockAbstractVcs(getProject());
    AllVcses.getInstance(getProject()).registerManually(myVcs);
    ChangeListManager.getInstance(getProject()).addChangeList("Default", "");

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

      @Override
      public Icon getIcon() {
        return null;
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
    AllVcses.getInstance(getProject()).unregisterManually(myVcs);
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
