// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack;

import com.google.gson.JsonElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.youtrack.model.YouTrackIssue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class YouTrackTask extends Task {
  private final YouTrackRepository myRepository;
  private final YouTrackIssue myIssue;
  private final TaskType myType;
  private final TaskState myIssueState;

  public YouTrackTask(@NotNull YouTrackRepository repository, @NotNull YouTrackIssue issue) {
    myRepository = repository;
    myIssue = issue;
    myIssueState = mapIssueStateToPredefinedValue();
    myType = mapIssueTypeToPredefinedValue();
  }

  @Override
  public @NotNull TaskType getType() {
    return myType;
  }

  @NotNull
  private TaskType mapIssueTypeToPredefinedValue() {
    String typeName = getCustomFieldStringValue("Type");
    if (typeName != null) {
      try {
        return TaskType.valueOf(StringUtil.toUpperCase(typeName));
      }
      catch (IllegalArgumentException ignored) {
      }
    }
    return TaskType.OTHER;
  }

  @Override
  public @Nullable TaskState getState() {
    return myIssueState;
  }

  @Nullable
  private TaskState mapIssueStateToPredefinedValue() {
    // TODO Make state field name configurable
    String stateName = getCustomFieldStringValue("State");
    if (stateName == null) {
      return null;
    }
    if (stateName.equals("Fixed")) {
      return TaskState.RESOLVED;
    }
    try {
      return TaskState.valueOf(StringUtil.toUpperCase(stateName));
    }
    catch (IllegalArgumentException ignored) {
    }
    return TaskState.OTHER;
  }

  @Nullable
  private String getCustomFieldStringValue(@NotNull String fieldName) {
    YouTrackIssue.CustomField stateField = ContainerUtil.find(myIssue.getCustomFields(), cf -> cf.getName().equals(fieldName));
    if (stateField != null) {
      JsonElement fieldValueElem = stateField.getValue();
      if (fieldValueElem != null && fieldValueElem.isJsonObject()) {
        JsonElement stateNameElem = fieldValueElem.getAsJsonObject().get("name");
        if (stateNameElem != null && stateNameElem.isJsonPrimitive() && stateNameElem.getAsJsonPrimitive().isString()) {
          return stateNameElem.getAsString();
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull String getId() {
    return myIssue.getId();
  }

  @Override
  public @NotNull TaskRepository getRepository() {
    return myRepository;
  }

  @Override
  public @Nls @NotNull String getSummary() {
    return myIssue.getSummary();
  }

  @Override
  public @Nls @NotNull String getDescription() {
    return myIssue.getDescription();
  }

  @Override
  public Comment @NotNull [] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @Override
  public @NotNull Icon getIcon() {
    return LocalTaskImpl.getIconFromType(getType(), true);
  }

  @Override
  public @NotNull Date getUpdated() {
    return new Date(myIssue.getUpdated());
  }

  @Override
  public @NotNull Date getCreated() {
    return new Date(myIssue.getCreated());
  }

  @Override
  public boolean isClosed() {
    return myIssue.getResolved() != 0;
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Override
  public @NotNull String getIssueUrl() {
    return myRepository.getUrl() + "/issue/" + getId();
  }
}
