package com.intellij.tasks.jira.jql.codeinsight;

/**
 * Custom fields types are not considered here
 *
 * @author Mikhail Golubev
 */
public enum JqlFieldType {
  VERSION,
  USER,
  CATEGORY,
  TEXT,
  COMPONENT,
  DATE,
  FILTER,
  ISSUE,
  SECURITY_LEVEL,
  DURATION,
  PRIORITY,
  RESOLUTION,
  STATUS,
  ISSUE_TYPE,
  PROJECT,
  NUMBER,
  // used for custom field and when field type is yet unknown,
  // e.g. in the beginning of new clause
  UNKNOWN
}
