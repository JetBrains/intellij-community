// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Auxiliary wrapper object for JIRA tracker responses.
 *
 * @author Mikhail Golubev
 */
@SuppressWarnings("FieldMayBeFinal") // fields may be read and reflectively set from GSON
public abstract class JiraResponseWrapper {

  private int startAt;
  private int maxResults;
  private int total;

  public int getStartAt() {
    return startAt;
  }

  public int getMaxResults() {
    return maxResults;
  }

  public int getTotal() {
    return total;
  }

  /**
   * JSON representation of issue differs dramatically between REST API 2.0 and 2.0alpha1,
   * that's why this wrapper was generified and JiraIssue extracted to abstract class
   */
  public static class Issues<T extends JiraIssue> extends JiraResponseWrapper {
    private List<T> issues = ContainerUtil.emptyList();

    public @NotNull List<T> getIssues() {
      return issues;
    }
  }

  public static class Comments extends JiraResponseWrapper {
    private List<JiraComment> comments = ContainerUtil.emptyList();

    public @NotNull List<JiraComment> getComments() {
      return comments;
    }
  }
}
