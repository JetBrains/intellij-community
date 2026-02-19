// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.util.List;

public class ValidationResult implements Comparable<ValidationResult> {
  public enum Kind {
    INFO, CONFLICT, ERROR
  }

  public enum Action {
    CREATE, UPDATE, DELETE, VALIDATE
  }

  public enum Option {
    NONE, IGNORE, KEEP, REPLACE, DELETE, KILL_PROCESS
  }

  public final Kind kind;
  public final String path;
  public final Action action;
  public final @UpdaterUI.Message String message;
  public final String details;
  public final List<Option> options;

  public ValidationResult(Kind kind, String path, Action action, @UpdaterUI.Message String message, Option... options) {
    this(kind, path, action, message, "", options);
  }

  public ValidationResult(Kind kind, String path, Action action, @UpdaterUI.Message String message, String details, Option... options) {
    this.kind = kind;
    this.path = path;
    this.action = action;
    this.message = message;
    this.details = details;
    this.options = List.of(options);
  }

  @Override
  public String toString() {
    var prefix = kind == Kind.CONFLICT ? "?" : kind == Kind.ERROR ? "!" : "";
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
