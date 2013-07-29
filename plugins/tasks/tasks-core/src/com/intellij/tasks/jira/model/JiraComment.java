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
package com.intellij.tasks.jira.model;

import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public class JiraComment {
  private JiraUser author;
  private JiraUser updateAuthor;
  private Date update;
  private Date created;
  private String id;
  private String self;
  private String body;

  public JiraUser getAuthor() {
    return author;
  }

  public JiraUser getUpdateAuthor() {
    return updateAuthor;
  }

  public Date getUpdate() {
    return update;
  }

  public Date getCreated() {
    return created;
  }

  public String getId() {
    return id;
  }

  public String getSelf() {
    return self;
  }

  public String getBody() {
    return body;
  }
}
