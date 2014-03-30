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

  @NotNull
  public JiraUser getAuthor() {
    return author;
  }

  @NotNull
  public JiraUser getUpdateAuthor() {
    return updateAuthor;
  }

  @NotNull
  public Date getUpdated() {
    return updated;
  }

  @NotNull
  public Date getCreated() {
    return created;
  }

  @NotNull
  public String getCommentUrl() {
    return self;
  }

  @NotNull
  public String getBody() {
    return body;
  }

  @Override
  public String toString() {
    return String.format("JiraComment(text='%s')", StringUtil.first(body, 30, true));
  }
}
