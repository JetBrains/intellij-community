package com.intellij.tasks.mantis;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * User: evgeny.zakrevsky
 * Date: 9/24/12
 */
public final class MantisProject {
  // Used for "All projects" option in settings
  public static final int UNDEFINED_PROJECT_ID = 0;

  public static MantisProject newUndefined() {
    return new MantisProject(0, "All Projects");
  }

  private List<MantisFilter> myFilters;

  private int id;
  private String name;

  @SuppressWarnings({"UnusedDeclaration"})
  public MantisProject() {
    // empty
  }

  public MantisProject(int id, @NotNull String name) {
    this.id = id;
    this.name = name;
  }

  @Attribute("id")
  public int getId() {
    return id;
  }

  public void setId(final int id) {
    this.id = id;
  }

  @Attribute("name")
  @NotNull
  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }


  public final boolean isUndefined() {
    return getId() == UNDEFINED_PROJECT_ID;
  }

  //@OptionTag(tag = "filters", nameAttribute = "")
  @AbstractCollection(surroundWithTag = false)
  @NotNull
  public List<MantisFilter> getFilters() {
    return myFilters == null? Collections.<MantisFilter>emptyList() : myFilters;
  }

  public void setFilters(@NotNull List<MantisFilter> filters) {
    myFilters = filters;
  }


  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MantisProject project = (MantisProject)o;

    if (id != project.id) return false;

    return true;
  }

  @Override
  public final int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return getName();
  }
}
