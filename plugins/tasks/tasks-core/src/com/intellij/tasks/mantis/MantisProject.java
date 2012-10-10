package com.intellij.tasks.mantis;

/**
 * User: evgeny.zakrevsky
 * Date: 9/24/12
 */
public class MantisProject {
  public final static MantisProject ALL_PROJECTS = new MantisProject(0, "All Projects");

  private int id;
  private String name;

  @SuppressWarnings({"UnusedDeclaration"})
  public MantisProject() {
  }

  public MantisProject(final int id, final String name) {
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
    return obj != null && obj instanceof MantisProject && ((MantisProject)obj).getId() == getId();
  }

  @Override
  public String toString() {
    return getName();
  }
}
