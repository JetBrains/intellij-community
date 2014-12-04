package com.intellij.tasks.jira.rest.api2.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.intellij.tasks.CustomTaskState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Mikhail Golubev
 */
public class JiraTransitionsWrapper {
  private List<JiraTransition> transitions = Collections.emptyList();

  static class JiraTransition {
    private int id;
    private String name;
    @SerializedName("to")
    private JiraTaskState target;
    private JsonObject fields;

    static class JiraTaskState {
      private int id;
      private String name;
    }
  }

  @NotNull
  public Set<CustomTaskState> getTransitions() {
    final Set<CustomTaskState> result = new LinkedHashSet<CustomTaskState>();
    for (JiraTransition transition : transitions) {
      final int transitionId = transition.id;
      final String transitionName = transition.target.name;
      String fieldName = null;
      if (transition.fields != null) {
        for (Map.Entry<String, JsonElement> field : transition.fields.entrySet()) {
          fieldName = field.getKey();
          final JsonObject fieldInfo = field.getValue().getAsJsonObject();
          if (fieldName.equals("resolution") && fieldInfo.get("required").getAsBoolean()) {
            for (JsonElement allowedValue : fieldInfo.getAsJsonArray("allowedValues")) {
              final String resolutionName = allowedValue.getAsJsonObject().get("name").getAsString();
              final int resolutionId = allowedValue.getAsJsonObject().get("id").getAsInt();
              result.add(new JiraCustomTaskState(transitionId, transitionName + " (" + resolutionName + ")", resolutionId));
            }
            break;
          }
        }
      }
      if (fieldName == null) {
        result.add(new JiraCustomTaskState(transitionId, transitionName, 0));
      }
    }
    return result;
  }
}
