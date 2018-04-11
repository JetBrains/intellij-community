/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.tasks.jira.rest.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Auxiliary wrapper object for JIRA tracker responses.
 *
 * @author Mikhail Golubev
 */
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
    private final List<T> issues = ContainerUtil.emptyList();

    @NotNull
    public List<T> getIssues() {
      return issues;
    }
  }

  public static class Comments extends JiraResponseWrapper {
    private final List<JiraComment> comments = ContainerUtil.emptyList();

    @NotNull
    public List<JiraComment> getComments() {
      return comments;
    }
  }
}
