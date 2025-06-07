// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.util.ObjectUtils;
import icons.TasksCoreIcons;
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
  // Deferred icon must be stored as a field because otherwise it's going to initiate repainting
  // of the containing component and will be re-built anew indefinitely.
  // It can be accessed not only in EDT, e.g. to get completion items for tasks.
  private volatile Icon myIcon;

  protected JiraTask(@NotNull TaskRepository repository) {
    myRepository = repository;
  }

  @Override
  public abstract @NotNull String getId();

  @Override
  public abstract @NotNull String getSummary();

  @Override
  public abstract String getDescription();

  @Override
  public abstract Comment @NotNull [] getComments();

  // iconUrl will be null in JIRA versions prior 5.x.x
  protected abstract @Nullable String getIconUrl();

  @Override
  public abstract @NotNull TaskType getType();

  @Override
  public abstract TaskState getState();

  @Override
  public abstract @Nullable Date getUpdated();

  @Override
  public abstract Date getCreated();

  @Override
  public final String getIssueUrl() {
    return myRepository.getUrl() + "/browse/" + getId();
  }

  @Override
  public final @NotNull Icon getIcon() {
    if (myIcon == null) {
      // getIconUrl() shouldn't be called before the instance is properly initialized
      final String iconUrl = getIconUrl();
      if (StringUtil.isEmpty(iconUrl)) {
        myIcon = TasksCoreIcons.Jira;
      }
      else {
        myIcon = new DeferredIconImpl<>(TasksCoreIcons.Jira, iconUrl, false, JiraTask::getIconByUrl);
      }
    }
    return myIcon;
  }

  @Override
  public final @NotNull TaskRepository getRepository() {
    return myRepository;
  }

  @Override
  public final boolean isClosed() {
    return getState() == TaskState.RESOLVED;
  }

  @Override
  public final boolean isIssue() {
    return true;
  }

  /**
   * Pick the appropriate issue type's icon by its URL, contained in JIRA's responses.
   * Icons will be lazily fetched using {@link CachedIconLoader}.
   *
   * @param iconUrl unique icon URL as returned from {@link #getIconUrl()}
   * @return task con.
   */
  protected static @NotNull Icon getIconByUrl(@Nullable String iconUrl) {
    return ObjectUtils.notNull(CachedIconLoader.getIcon(iconUrl), AllIcons.FileTypes.Any_type);
  }

  /**
   * Map unique state id to corresponding {@link TaskState} item.
   *
   * @param id issue's state numeric id
   * @return {@link TaskState} item or {@code null}, if none matches
   */
  @SuppressWarnings("MethodMayBeStatic")
  protected final @Nullable TaskState getStateById(int id) {
    return switch (id) {
      case 1 -> TaskState.OPEN;
      case 3 -> TaskState.IN_PROGRESS;
      case 4 -> TaskState.REOPENED;
      case 5,  // resolved
           6 -> // closed
        TaskState.RESOLVED;
      default -> null;
    };
  }

  /**
   * Map task's type name in JIRA's API to corresponding {@link TaskType} item.
   *
   * @param type issue's type name
   * @return {@link TaskType} item or {@link TaskType#OTHER}, if none matches
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
