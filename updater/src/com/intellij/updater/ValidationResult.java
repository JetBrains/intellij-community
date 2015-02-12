package com.intellij.updater;

import java.util.Arrays;
import java.util.List;

public class ValidationResult implements Comparable<ValidationResult> {
  public enum Kind {
    INFO, CONFLICT, ERROR
  }

  public enum Action {
    CREATE("Create"), UPDATE("Update"), DELETE("Delete"), NO_ACTION(""), VALIDATE("Validate");

    private final String myDisplayString;

    Action(String displayString) {
      myDisplayString = displayString;
    }

    @Override
    public String toString() {
      return myDisplayString;
    }
  }

  public enum Option {
    NONE, IGNORE, KEEP, REPLACE, DELETE, KILL_PROCESS
  }

  public static final String ABSENT_MESSAGE = "Absent";
  public static final String MODIFIED_MESSAGE = "Modified";
  public static final String ACCESS_DENIED_MESSAGE = "Access denied";
  public static final String ALREADY_EXISTS_MESSAGE = "Already exists";

  public final Kind kind;
  public final String path;
  public final Action action;
  public final String message;
  public final List<Option> options;

  public ValidationResult(Kind kind, String path, Action action, String message, Option... options) {
    this.kind = kind;
    this.path = path;
    this.action = action;
    this.message = message;
    this.options = Arrays.asList(options);
  }

  @Override
  public String toString() {
    String prefix;
    switch (kind) {
      case CONFLICT:
        prefix = "?";
        break;
      case ERROR:
        prefix = "!";
        break;
      default:
        prefix = "";
    }
    return prefix + action + " " + path + ": " + message + " (" + options + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ValidationResult result = (ValidationResult)o;

    if (action != result.action) return false;
    if (kind != result.kind) return false;
    if (message != null ? !message.equals(result.message) : result.message != null) return false;
    if (options != null ? !options.equals(result.options) : result.options != null) return false;
    if (path != null ? !path.equals(result.path) : result.path != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = kind != null ? kind.hashCode() : 0;
    result = 31 * result + (path != null ? path.hashCode() : 0);
    result = 31 * result + (action != null ? action.hashCode() : 0);
    result = 31 * result + (message != null ? message.hashCode() : 0);
    result = 31 * result + (options != null ? options.hashCode() : 0);
    return result;
  }

  public int compareTo(ValidationResult o) {
    return path.compareToIgnoreCase(o.path);
  }
}
