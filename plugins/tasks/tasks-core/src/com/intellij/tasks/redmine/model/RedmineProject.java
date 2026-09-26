// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.redmine.model;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class RedmineProject {
  private int id;
  @Mandatory
  private String name;
  // Missing in Project information sent as part of issue
  private String identifier;
  // Available only for subprojects
  private RedmineProject parent;

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RedmineProject project)) return false;

    return id == project.id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Attribute("id")
  public int getId() {
    return id;
  }

  /**
   * For serialization purposes only
   */
  public void setId(int id) {
    this.id = id;
  }

  public @NotNull @NlsSafe String getName() {
    return name;
  }

  public @Nullable String getIdentifier() {
    return identifier;
  }

  /**
   * For serialization purposes only
   */
  @Attribute("identifier")
  public void setIdentifier(@NotNull String identifier) {
    this.identifier = identifier;
  }

  public @Nullable RedmineProject getParent() {
    return parent;
  }

  @Override
  public final String toString() {
    return getName();
  }
}
