// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.model;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public class JiraComment {
  private JiraUser author;
  private JiraUser updateAuthor;
  private Date updated;
  private Date created;
  private String self;
  private String body;

  public @NotNull JiraUser getAuthor() {
    return author;
  }

  public @NotNull JiraUser getUpdateAuthor() {
    return updateAuthor;
  }

  public @NotNull Date getUpdated() {
    return updated;
  }

  public @NotNull Date getCreated() {
    return created;
  }

  public @NotNull String getCommentUrl() {
    return self;
  }

  public @NotNull @NlsSafe String getBody() {
    return body;
  }

  @Override
  public String toString() {
    return String.format("JiraComment(text='%s')", StringUtil.first(body, 30, true));
  }
}
