/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.tasks.redmine.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.tasks.Comment;
import com.intellij.tasks.impl.gson.Mandatory;
import com.intellij.tasks.impl.gson.RestModel;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Marlin Cremers
 */
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class RedmineIssueJournal extends Comment {
  private int id;

  private String notes;

  @Mandatory
  @SerializedName("created_on")
  private Date created;

  private RedmineUser user;

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RedmineIssueJournal)) return false;

    RedmineIssueJournal journal = (RedmineIssueJournal)o;

    return id == journal.id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Attribute("id")
  public int getId() {
    return id;
  }

  /**
   * For serialization purposes only
   */
  public void setId(int id) {
    this.id = id;
  }

  @Nullable
  public String getNotes() {
    return notes;
  }

  @NotNull
  public Date getCreated() {
    return created;
  }

  @NotNull
  public RedmineUser getUser() {
    return user;
  }

  @Override
  public String getText() {
    return getNotes();
  }

  @Nullable
  @Override
  public String getAuthor() {
    return getUser().getName();
  }

  @Nullable
  @Override
  public Date getDate() {
    return getCreated();
  }

  @Override
  public final String toString() {
    return getNotes();
  }
}
