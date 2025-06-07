// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.bugzilla;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import icons.TasksCoreIcons;
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

  @Override
  public @NotNull String getId() {
    return String.valueOf(myResponse.get("id"));
  }

  @Override
  public @NotNull String getSummary() {
    return (String)myResponse.get("summary");
  }

  @Override
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public Comment @NotNull [] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Bugzilla;
  }

  @Override
  public @NotNull TaskType getType() {
    String severity = (String)myResponse.get("severity");
    return severity.equalsIgnoreCase("enhancement")? TaskType.FEATURE : TaskType.BUG;
  }

  @Override
  public @Nullable TaskState getState() {
    final String status = (String)myResponse.get("status");
    return switch (status) {
      case "IN_PROGRESS" -> TaskState.IN_PROGRESS;
      case "CONFIRMED" -> TaskState.OPEN;
      case "UNCONFIRMED" -> TaskState.SUBMITTED;
      case "RESOLVED" -> TaskState.RESOLVED;
      default -> TaskState.OTHER;
    };
  }

  @Override
  public @Nullable Date getUpdated() {
    return (Date)myResponse.get("last_change_time");
  }

  @Override
  public @Nullable Date getCreated() {
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

  @Override
  public @Nullable String getIssueUrl() {
    String repositoryUrl = myRepository.getUrl();
    repositoryUrl = StringUtil.trimEnd(repositoryUrl, "xmlrpc.cgi");
    return repositoryUrl + "/show_bug.cgi?id=" + getId();
  }

  @Override
  public @Nullable TaskRepository getRepository() {
    return myRepository;
  }
}
