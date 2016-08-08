package com.intellij.tasks.jira.jql.codeinsight;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
public enum JqlStandardField {
  AFFECTED_VERSION("affectedVersion", JqlFieldType.VERSION),
  ASSIGNEE("assignee", JqlFieldType.USER),
  CATEGORY("category", JqlFieldType.CATEGORY),
  COMMENT("comment", JqlFieldType.TEXT),
  COMPONENT("component", JqlFieldType.COMPONENT),
  CREATED("created", JqlFieldType.DATE),
  CREATED_DATE("createdDate", JqlFieldType.DATE),
  DESCRIPTION("description", JqlFieldType.TEXT),
  DUE("due", JqlFieldType.DATE),
  DUE_DATE("dueDate", JqlFieldType.DATE),
  ENVIRONMENT("environment", JqlFieldType.TEXT),
  FILTER("filter", JqlFieldType.FILTER),
  REQUEST("request", JqlFieldType.FILTER),
  SAVED_FILTER("savedFilter", JqlFieldType.FILTER),
  SEARCH_REQUEST("searchRequest", JqlFieldType.FILTER),
  FIX_VERSION("fixVersion", JqlFieldType.VERSION),
  ISSUE_KEY("issueKey", JqlFieldType.ISSUE),
  ID("id", JqlFieldType.ISSUE),
  ISSUE("issue", JqlFieldType.ISSUE),
  KEY("key", JqlFieldType.ISSUE),
  LAST_VIEWED("lastViewed", JqlFieldType.DATE),
  LEVEL("level", JqlFieldType.SECURITY_LEVEL),
  ORIGINAL_ESTIMATE("originalEstimate", JqlFieldType.DURATION),
  TIME_ORIGINAL_ESTIMATE("timeOriginalEstimate", JqlFieldType.DURATION),
  PARENT("parent", JqlFieldType.ISSUE),
  PRIORITY("priority", JqlFieldType.PRIORITY),
  PROJECT("project", JqlFieldType.PROJECT),
  REMAINING_ESTIMATE("remainingEstimate", JqlFieldType.DURATION),
  TIME_ESTIMATE("timeEstimate", JqlFieldType.DURATION),
  REPORTER("reporter", JqlFieldType.USER),
  RESOLUTION("resolution", JqlFieldType.RESOLUTION),
  RESOLVED("resolved", JqlFieldType.DATE),
  RESOLUTION_DATE("resolutionDate", JqlFieldType.DATE),
  STATUS("status", JqlFieldType.STATUS),
  SUMMARY("summary", JqlFieldType.TEXT),
  // So called "master-field". Search in summary, description, environment,
  // comments and custom fields
  TEXT("text", JqlFieldType.TEXT),
  TYPE("type", JqlFieldType.ISSUE_TYPE),
  ISSUE_TYPE("issueType", JqlFieldType.ISSUE_TYPE),
  TIME_SPENT("timeSpent", JqlFieldType.DURATION),
  UPDATED("updated", JqlFieldType.DATE),
  UPDATED_DATE("updatedDate", JqlFieldType.DATE),
  VOTER("voter", JqlFieldType.USER),
  VOTES("votes", JqlFieldType.NUMBER),
  WATCHER("watcher", JqlFieldType.USER),
  WATCHERS("watchers", JqlFieldType.NUMBER),
  WORK_RATION("workRatio", JqlFieldType.NUMBER);

  private final String myName;
  private final JqlFieldType myType;
  JqlStandardField(String name, JqlFieldType type) {
    myName = name;
    myType = type;
  }

  public String getName() {
    return myName;
  }

  public JqlFieldType getType() {
    return myType;
  }

  private static final JqlStandardField[] VALUES = values();
  private static final Map<String, JqlStandardField> NAME_LOOKUP = ContainerUtil.newMapFromValues(
    ContainerUtil.iterate(VALUES),
    new Convertor<JqlStandardField, String>() {
      @Override
      public String convert(JqlStandardField field) {
        return field.getName();
      }
    }
  );

  public static JqlStandardField byName(String name) {
    return NAME_LOOKUP.get(name);
  }

  private static final MultiMap<JqlFieldType, String> TYPE_LOOKUP = new MultiMap<>();
  static {
    for (JqlStandardField field : VALUES) {
      TYPE_LOOKUP.putValue(field.getType(), field.getName());
    }
  }

  public static Collection<String> allOfType(JqlFieldType type) {
    return type == JqlFieldType.UNKNOWN? ALL_FIELD_NAMES : new ArrayList<>(TYPE_LOOKUP.get(type));
  }

  public static final List<String> ALL_FIELD_NAMES = ContainerUtil.map2List(VALUES, field -> field.myName);

  public static JqlFieldType typeOf(String name) {
    for (Map.Entry<JqlFieldType, Collection<String>> entry : TYPE_LOOKUP.entrySet()) {
      if (entry.getValue().contains(name)) {
        return entry.getKey();
      }
    }
    return JqlFieldType.UNKNOWN;
  }
}
