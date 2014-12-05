package com.intellij.tasks.jira.rest.api2.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.jira.rest.model.JiraCustomTaskState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Mikhail Golubev
 */
public class JiraTransitionsWrapperApi2 {
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
              result.add(new JiraCustomTaskState(transitionName + " (" + resolutionName + ")", transitionId, resolutionName));
            }
            break;
          }
        }
      }
      if (fieldName == null) {
        result.add(new JiraCustomTaskState(transitionName, transitionId, null));
      }
    }
    return result;
  }
}
