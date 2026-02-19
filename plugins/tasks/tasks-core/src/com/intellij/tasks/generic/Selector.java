// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.generic;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */

@Tag("selector")
public final class Selector {
  private @NotNull String myName = "";
  private @NotNull String myPath = "";

  /**
   * Serialization constructor
   */
  public Selector() {
    // empty
  }

  public Selector(@NotNull String name) {
    this(name, "");
  }

  public Selector(@NotNull String name, @NotNull String path) {
    myName = name;
    myPath = path;
  }

  public Selector(Selector other) {
    myName = other.myName;
    myPath = other.myPath;
  }

  @Attribute("name")
  public @NotNull String getName() {
    return myName;
  }

  @Attribute("path")
  public @NotNull String getPath() {
    return myPath;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public void setPath(@NotNull String path) {
    myPath = path;
  }

  @Override
  public Selector clone() {
    return new Selector(this);
  }

  @Override
  public String toString() {
    return String.format("Selector(name='%s', path='%s')", getName(), getPath());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Selector selector = (Selector)o;

    if (!myName.equals(selector.myName)) return false;
    if (!myPath.equals(selector.myPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = (31 * result) + (myPath.hashCode());
    return result;
  }
}
