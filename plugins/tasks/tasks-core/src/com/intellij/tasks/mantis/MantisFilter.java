package com.intellij.tasks.mantis;

/**
 * User: evgeny.zakrevsky
 * Date: 9/24/12
 */
public class MantisFilter {
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
  public boolean equals(final Object obj) {
    return obj != null && obj instanceof MantisFilter && ((MantisFilter)obj).getId() == getId();
  }

  @Override
  public String toString() {
    return getName();
  }
}
