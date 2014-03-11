package com.intellij.tasks.jira;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * Base class containing common interpretation of issues object's fields in
 * JIRA's XML-RPC and REST interfaces.
 *
 * @author Mikhail Golubev
 */
public abstract class JiraTask extends Task {
  protected final TaskRepository myRepository;

  protected JiraTask(@NotNull TaskRepository repository) {
    myRepository = repository;
  }

  @NotNull
  public abstract String getId();

  @NotNull
  public abstract String getSummary();

  public abstract String getDescription();

  @NotNull
  public abstract Comment[] getComments();

  // iconUrl will be null in JIRA versions prior 5.x.x
  @Nullable
  protected abstract String getIconUrl();

  @NotNull
  @Override
  public abstract TaskType getType();

  @Override
  public abstract TaskState getState();

  @Nullable
  @Override
  public abstract Date getUpdated();

  @Override
  public abstract Date getCreated();

  @Override
  public final String getIssueUrl() {
    return myRepository.getUrl() + "/browse/" + getId();
  }

  @Override
  @NotNull
  public final Icon getIcon() {
    return getIconByUrl(getIconUrl());
  }

  @Nullable
  @Override
  public final TaskRepository getRepository() {
    return myRepository;
  }

  @Override
  public final boolean isClosed() {
    return getState() == TaskState.RESOLVED;
  }

  public final boolean isIssue() {
    return true;
  }

  /**
   * Pick appropriate issue type's icon by its URL, contained in JIRA's responses.
   * Icons will be lazily fetched using {@link CachedIconLoader}.
   *
   * @param iconUrl unique icon URL as returned from {@link #getIconUrl()}
   * @return task con.
   */
  @NotNull
  protected final Icon getIconByUrl(@Nullable String iconUrl) {
    if (StringUtil.isEmpty(iconUrl)) {
      return TasksIcons.Jira;
    }
    Icon icon;
    if (isClosed()) {
      icon = CachedIconLoader.getDisabledIcon(iconUrl);
    }
    else {
      icon = CachedIconLoader.getIcon(iconUrl);
    }
    return icon != null ? icon : TasksIcons.Other;
  }

  /**
   * Map unique state id to corresponding {@link TaskState} item.
   *
   * @param id issue's state numeric id
   * @return {@link TaskState} item or {@code null}, if none matches
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  protected final TaskState getStateById(int id) {
    switch (id) {
      case 1:
        return TaskState.OPEN;
      case 3:
        return TaskState.IN_PROGRESS;
      case 4:
        return TaskState.REOPENED;
      case 5: // resolved
      case 6: // closed
        return TaskState.RESOLVED;
      default:
        return null;
    }
  }

  /**
   * Map task's type name in JIRA's API to corresponding {@link TaskType} item.
   *
   * @param type issue's type name
   * @return {@link TaskType} item or {@link TaskType.OTHER}, if none matches
   */
  @SuppressWarnings("MethodMayBeStatic")
  protected final TaskType getTypeByName(@Nullable String type) {
    if (type == null) {
      return TaskType.OTHER;
    }
    else if ("Bug".equals(type)) {
      return TaskType.BUG;
    }
    else if ("Exception".equals(type)) {
      return TaskType.EXCEPTION;
    }
    else if ("New Feature".equals(type)) {
      return TaskType.FEATURE;
    }
    else {
      return TaskType.OTHER;
    }
  }
}
