package com.intellij.tasks.jira.rest.api2.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.CustomTaskState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Mikhail Golubev
 */
@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class JiraTransitionsWrapperApi2 {
  private static final Logger LOG = Logger.getInstance(JiraTransitionsWrapperApi2.class);
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
    final Set<CustomTaskState> result = new LinkedHashSet<>();
    nextTransition:
    for (JiraTransition transition : transitions) {
      final String stateName = transition.target.name;
      final List<String> resolutions = new ArrayList<>();
      if (transition.fields != null) {
        for (Map.Entry<String, JsonElement> field : transition.fields.entrySet()) {
          final String fieldName = field.getKey();
          final JsonObject fieldInfo = field.getValue().getAsJsonObject();
          if (fieldInfo.get("required").getAsBoolean()) {
            if (fieldName.equals("resolution")) {
              for (JsonElement allowedValue : fieldInfo.getAsJsonArray("allowedValues")) {
                resolutions.add(allowedValue.getAsJsonObject().get("name").getAsString());
              }
            }
            else {
              LOG.info("Unknown required field '" + fieldName + "' for transition '" + stateName + "'");
              continue nextTransition;
            }
          }
        }
      }
      if (resolutions.isEmpty()) {
        result.add(new CustomTaskState(String.valueOf(transition.id), stateName));
      }
      else {
        for (String resolution : resolutions) {
          result.add(new CustomTaskState(transition.id + ":" + resolution, stateName + " (" + resolution + ")"));
        }
      }
    }
    return result;
  }
}
