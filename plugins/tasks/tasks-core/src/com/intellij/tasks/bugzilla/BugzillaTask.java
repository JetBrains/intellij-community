package com.intellij.tasks.bugzilla;

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
    if (repositoryUrl.endsWith("xmlrpc.cgi")) {
      repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - "xmlrpc.cgi".length());
    }
    return repositoryUrl + "/show_bug.cgi?id=" + getId();
  }

  @Nullable
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }
}
