// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.youtrack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.youtrack.model.YouTrackIssue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class YouTrackTask extends Task {
  private final YouTrackRepository myRepository;
  private final YouTrackIssue myIssue;
  private final TaskType myType;
  private final TaskState myIssueState;
  private final Map<String, CustomTaskProperty> myProperties;

  public static final String PROP_PRIORITY = "priority";
  public static final String PROP_TYPE = "type";
  public static final String PROP_STATE = "state";
  public static final String PROP_ASSIGNEE = "assignee";

  private static final List<String> PROPERTIES_TO_SHOW_IN_PREVIEW = List.of(PROP_PRIORITY, PROP_TYPE, PROP_STATE, PROP_ASSIGNEE);

  public YouTrackTask(@NotNull YouTrackRepository repository, @NotNull YouTrackIssue issue) {
    myRepository = repository;
    myIssue = issue;
    myIssueState = mapIssueStateToPredefinedValue();
    myType = mapIssueTypeToPredefinedValue();
    myProperties = buildProperties();
  }

  @Override
  public @NotNull TaskType getType() {
    return myType;
  }

  private @NotNull TaskType mapIssueTypeToPredefinedValue() {
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

  private @Nullable TaskState mapIssueStateToPredefinedValue() {
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

  private @Nullable String getCustomFieldStringValue(@NotNull String fieldName) {
    YouTrackIssue.CustomField stateField = ContainerUtil.find(myIssue.getCustomFields(), cf -> cf.getName().equals(fieldName));
    if (stateField != null) {
      JsonElement fieldValueElem = stateField.getValue();
      if (fieldValueElem == null) {
        return null;
      }
      List<JsonObject> elements;
      if (fieldValueElem.isJsonArray()) {
        elements = ContainerUtil.filterIsInstance(fieldValueElem.getAsJsonArray().asList(), JsonObject.class);
      }
      else if (fieldValueElem.isJsonObject()) {
        elements = Collections.singletonList(fieldValueElem.getAsJsonObject());
      }
      else {
        return null;
      }

      var result = elements.stream().map(it -> it.get("name"))
        .filter(it -> it != null && it.isJsonPrimitive() && it.getAsJsonPrimitive().isString())
        .map(JsonElement::getAsString)
        .toList();
      if (result.isEmpty()) {
        return null;
      }
      return StringUtil.join(result, ", ");
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
  public @NotNull Map<@NotNull String, @NotNull CustomTaskProperty> getCustomProperties() {
    return myProperties;
  }

  @Override
  public @NotNull List<@NotNull String> getPropertiesToShowInPreview() {
    return PROPERTIES_TO_SHOW_IN_PREVIEW;
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

  private Map<String, CustomTaskProperty> buildProperties() {
    HashMap<String, CustomTaskProperty> properties = new LinkedHashMap<>();
    addCustomField(properties, PROP_STATE, "State", TaskBundle.message("task.preview.state"));
    addCustomField(properties, PROP_TYPE, "Type", TaskBundle.message("task.preview.type"));
    addCustomField(properties, PROP_PRIORITY, "Priority", TaskBundle.message("task.preview.priority"));
    addCustomField(properties, PROP_ASSIGNEE, "Assignee", TaskBundle.message("task.preview.assignee"));
    return Collections.unmodifiableMap(properties);
  }

  private void addCustomField(HashMap<String, CustomTaskProperty> properties,
                              String propertyName,
                              String fieldName,
                              @Nls String displayName) {
    String value = getCustomFieldStringValue(fieldName);
    if (value != null) {
      properties.put(propertyName, new CustomTaskProperty(displayName, value, null));
    }
  }
}
