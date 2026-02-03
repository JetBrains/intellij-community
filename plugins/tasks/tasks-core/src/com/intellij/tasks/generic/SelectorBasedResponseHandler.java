// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.generic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class SelectorBasedResponseHandler extends ResponseHandler {
  private static final Logger LOG = Logger.getInstance(SelectorBasedResponseHandler.class);

  // Supported selector names
  protected static final @NonNls String TASKS = "tasks";

  protected static final @NonNls String SUMMARY = "summary";
  protected static final @NonNls String DESCRIPTION = "description";
  protected static final @NonNls String ISSUE_URL = "issueUrl";
  protected static final @NonNls String CLOSED = "closed";
  protected static final @NonNls String UPDATED = "updated";
  protected static final @NonNls String CREATED = "created";

  protected static final @NonNls String SINGLE_TASK_ID = "singleTask-id";
  protected static final @NonNls String SINGLE_TASK_SUMMARY = "singleTask-summary";
  protected static final @NonNls String SINGLE_TASK_DESCRIPTION = "singleTask-description";
  protected static final @NonNls String SINGLE_TASK_ISSUE_URL = "singleTask-issueUrl";
  protected static final @NonNls String SINGLE_TASK_CLOSED = "singleTask-closed";
  protected static final @NonNls String SINGLE_TASK_UPDATED = "singleTask-updated";
  protected static final @NonNls String SINGLE_TASK_CREATED = "singleTask-created";
  protected static final @NonNls String ID = "id";

  protected LinkedHashMap<String, Selector> selectors = new LinkedHashMap<>();

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  protected SelectorBasedResponseHandler() {
    // empty
  }

  protected SelectorBasedResponseHandler(@NotNull GenericRepository repository) {
    super(repository);
    // standard selectors
    setSelectors(List.of(
      // matched against a list of tasks at whole downloaded from "taskListUrl"
      new Selector(TASKS),

      // matched against single tasks extracted from the list downloaded from "taskListUrl"
      new Selector(ID),
      new Selector(SUMMARY),
      new Selector(DESCRIPTION),
      new Selector(UPDATED),
      new Selector(CREATED),
      new Selector(CLOSED),
      new Selector(ISSUE_URL),

      // matched against a single task downloaded from "singleTaskUrl"
      new Selector(SINGLE_TASK_ID),
      new Selector(SINGLE_TASK_SUMMARY),
      new Selector(SINGLE_TASK_DESCRIPTION),
      new Selector(SINGLE_TASK_UPDATED),
      new Selector(SINGLE_TASK_CREATED),
      new Selector(SINGLE_TASK_CLOSED),
      new Selector(SINGLE_TASK_ISSUE_URL)
    ));
  }

  @XCollection(propertyElementName = "selectors")
  public @NotNull List<Selector> getSelectors() {
    return new ArrayList<>(selectors.values());
  }

  public void setSelectors(@NotNull List<Selector> selectors) {
    this.selectors.clear();
    for (Selector selector : selectors) {
      this.selectors.put(selector.getName(), selector);
    }
  }

  /**
   * Only predefined selectors should be accessed.
   */
  protected @NotNull Selector getSelector(@NotNull String name) {
    return selectors.get(name);
  }

  protected @NotNull String getSelectorPath(@NotNull String name) {
    return getSelector(name).getPath();
  }

  @Override
  public @NotNull JComponent getConfigurationComponent(@NotNull Project project) {
    FileType fileType = getResponseType().getSelectorFileType();
    HighlightedSelectorsTable table = new HighlightedSelectorsTable(fileType, project, getSelectors());
    return new JBScrollPane(table);
  }

  @Override
  public SelectorBasedResponseHandler clone() {
    SelectorBasedResponseHandler clone = (SelectorBasedResponseHandler)super.clone();
    clone.selectors = new LinkedHashMap<>(selectors.size());
    for (Selector selector : selectors.values()) {
      clone.selectors.put(selector.getName(), selector.clone());
    }
    return clone;
  }

  @Override
  public boolean isConfigured() {
    Selector idSelector = getSelector(ID);
    if (StringUtil.isEmpty(idSelector.getPath())) return false;
    Selector summarySelector = getSelector(SUMMARY);
    if (StringUtil.isEmpty(summarySelector.getPath()) && !repository.getDownloadTasksInSeparateRequests()) return false;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SelectorBasedResponseHandler handler)) return false;

    if (!selectors.equals(handler.selectors)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return selectors.hashCode();
  }

  @Override
  public final Task @NotNull [] parseIssues(@NotNull String response, int max) throws Exception {
    if (StringUtil.isEmpty(getSelectorPath(TASKS)) ||
        StringUtil.isEmpty(getSelectorPath(ID)) ||
        (StringUtil.isEmpty(getSelectorPath(SUMMARY)) && !repository.getDownloadTasksInSeparateRequests())) {
      throw new Exception("Selectors 'tasks', 'id' and 'summary' are mandatory");
    }
    List<Object> tasks = selectTasksList(response, max);
    LOG.debug(String.format("Total %d tasks extracted from response", tasks.size()));
    List<Task> result = new ArrayList<>(tasks.size());
    for (Object context : tasks) {
      String id = selectString(getSelector(ID), context);
      GenericTask task;
      if (repository.getDownloadTasksInSeparateRequests()) {
        task = new GenericTask(id, "", repository);
      }
      else {
        String summary = selectString(getSelector(SUMMARY), context);
        assert id != null && summary != null;
        task = new GenericTask(id, summary, repository);
        String description = selectString(getSelector(DESCRIPTION), context);
        if (description != null) {
          task.setDescription(description);
        }
        String issueUrl = selectString(getSelector(ISSUE_URL), context);
        if (issueUrl != null) {
          task.setIssueUrl(issueUrl);
        }
        Boolean closed = selectBoolean(getSelector(CLOSED), context);
        if (closed != null) {
          task.setClosed(closed);
        }
        Date updated = selectDate(getSelector(UPDATED), context);
        if (updated != null) {
          task.setUpdated(updated);
        }
        Date created = selectDate(getSelector(CREATED), context);
        if (created != null) {
          task.setCreated(created);
        }
      }
      result.add(task);
    }
    return result.toArray(Task.EMPTY_ARRAY);
  }

  private @Nullable Date selectDate(@NotNull Selector selector, @NotNull Object context) throws Exception {
    String s = selectString(selector, context);
    if (s == null) {
      return null;
    }
    return TaskUtil.parseDate(s);
  }

  protected @Nullable Boolean selectBoolean(@NotNull Selector selector, @NotNull Object context) throws Exception {
    String s = selectString(selector, context);
    if (s == null) {
      return null;
    }
    s = StringUtil.toLowerCase(s.trim());
    if (s.equals("true")) {
      return true;
    }
    else if (s.equals("false")) {
      return false;
    }
    throw new Exception(
      String.format("Expression '%s' should match boolean value. Got '%s' instead", selector.getName(), s));
  }

  protected abstract @NotNull List<Object> selectTasksList(@NotNull String response, int max) throws Exception;

  protected abstract @Nullable @Nls String selectString(@NotNull Selector selector, @NotNull Object context) throws Exception;

  @Override
  public final @NotNull Task parseIssue(@NotNull String response) throws Exception {
    if (StringUtil.isEmpty(getSelectorPath(SINGLE_TASK_ID)) ||
        StringUtil.isEmpty(getSelectorPath(SINGLE_TASK_SUMMARY))) {
      throw new Exception("Selectors 'singleTask-id' and 'singleTask-summary' are mandatory");
    }

    String id = selectString(getSelector(SINGLE_TASK_ID), response);
    String summary = selectString(getSelector(SINGLE_TASK_SUMMARY), response);
    assert id != null && summary != null;
    GenericTask task = new GenericTask(id, summary, repository);
    String description = selectString(getSelector(SINGLE_TASK_DESCRIPTION), response);
    if (description != null) {
      task.setDescription(description);
    }
    String issueUrl = selectString(getSelector(SINGLE_TASK_ISSUE_URL), response);
    if (issueUrl != null) {
      task.setIssueUrl(issueUrl);
    }
    Boolean closed = selectBoolean(getSelector(SINGLE_TASK_CLOSED), response);
    if (closed != null) {
      task.setClosed(closed);
    }
    Date updated = selectDate(getSelector(SINGLE_TASK_UPDATED), response);
    if (updated != null) {
      task.setUpdated(updated);
    }
    Date created = selectDate(getSelector(SINGLE_TASK_CREATED), response);
    if (created != null) {
      task.setCreated(created);
    }
    return task;
  }
}
