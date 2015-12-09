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
package com.intellij.tasks.bugzilla;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;
import java.util.Hashtable;

/**
 * @author Mikhail Golubev
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class BugzillaTask extends Task {
  private final Hashtable<String, Object> myResponse;
  private final BugzillaRepository myRepository;

  public BugzillaTask(@NotNull Hashtable<String, Object> xmlRpcResponse, @NotNull BugzillaRepository repository) {
    myResponse = xmlRpcResponse;
    myRepository = repository;
  }

  @NotNull
  @Override
  public String getId() {
    return String.valueOf(myResponse.get("id"));
  }

  @NotNull
  @Override
  public String getSummary() {
    return (String)myResponse.get("summary");
  }

  @Nullable
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
    return TasksIcons.Bugzilla;
  }

  @NotNull
  @Override
  public TaskType getType() {
    String severity = (String)myResponse.get("severity");
    return severity.equalsIgnoreCase("enhancement")? TaskType.FEATURE : TaskType.BUG;
  }

  @Nullable
  @Override
  public TaskState getState() {
    final String status = (String)myResponse.get("status");
    if (status.equals("IN_PROGRESS")) {
      return TaskState.IN_PROGRESS;
    }
    else if (status.equals("CONFIRMED")) {
      return TaskState.OPEN;
    }
    else if (status.equals("UNCONFIRMED")) {
      return TaskState.SUBMITTED;
    }
    else if (status.equals("RESOLVED")) {
      return TaskState.RESOLVED;
    }
    return TaskState.OTHER;
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return (Date)myResponse.get("last_change_time");
  }

  @Nullable
  @Override
  public Date getCreated() {
    return (Date)myResponse.get("creation_time");
  }

  @Override
  public boolean isClosed() {
    return !(Boolean)myResponse.get("is_open");
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Nullable
  @Override
  public String getIssueUrl() {
    String repositoryUrl = myRepository.getUrl();
    repositoryUrl = StringUtil.trimEnd(repositoryUrl, "xmlrpc.cgi");
    return repositoryUrl + "/show_bug.cgi?id=" + getId();
  }

  @Nullable
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }
}
