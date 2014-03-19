package com.intellij.tasks.mantis;

import org.jetbrains.annotations.NotNull;

/**
 * User: evgeny.zakrevsky
 * Date: 9/24/12
 */
public final class MantisFilter implements Comparable<MantisFilter> {
  public final static MantisFilter LAST_TASKS = new MantisFilter(0, "[Last tasks]");

  private int id;
  private String name;

  @SuppressWarnings({"UnusedDeclaration"})
  public MantisFilter() {
  }

  public MantisFilter(final int id, final String name) {
    this.id = id;
    this.name = name;
  }

  public int getId() {
    return id;
  }

  public void setId(final int id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return id == ((MantisFilter)o).id;
  }

  @Override
  public final int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public int compareTo(@NotNull MantisFilter o) {
    return getName().compareTo(o.getName());
  }
}
