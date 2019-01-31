// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    if (kind != result.kind) return false;
    if (!path.equals(result.path)) return false;
    if (action != result.action) return false;
    if (!message.equals(result.message)) return false;
    if (!options.equals(result.options)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = kind.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + action.hashCode();
    result = 31 * result + message.hashCode();
    result = 31 * result + options.hashCode();
    return result;
  }

  @Override
  public int compareTo(ValidationResult o) {
    return path.compareToIgnoreCase(o.path);
  }
}