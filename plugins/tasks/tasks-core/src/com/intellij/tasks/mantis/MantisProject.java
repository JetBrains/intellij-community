/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.tasks.mantis;

import com.intellij.tasks.mantis.model.ProjectData;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MantisProject {
  // Used for "All projects" option in settings
  public static final int UNSPECIFIED_PROJECT_ID = 0;

  public static MantisProject newUndefined() {
    return new MantisProject(0, "-- from all projects --");
  }

  private List<MantisFilter> myFilters = new ArrayList<>();

  private int myId;
  private String myName;

  @SuppressWarnings({"UnusedDeclaration"})
  public MantisProject() {
    // empty
  }

  public MantisProject(int id, @NotNull String name) {
    this.myId = id;
    this.myName = name;
  }

  public MantisProject(@NotNull ProjectData data) {
    myId = data.getId().intValue();
    myName = data.getName();
  }

  @Attribute("id")
  public int getId() {
    return myId;
  }

  public void setId(final int id) {
    this.myId = id;
  }

  @Attribute("name")
  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    this.myName = name;
  }


  public final boolean isUnspecified() {
    return getId() == UNSPECIFIED_PROJECT_ID;
  }

  //@OptionTag(tag = "filters", nameAttribute = "")
  //@XCollection

  /**
   * Filters here are used only to simplify combo boxes management and are refreshed every time when settings
   * are opened or user hit "Login" button. Thus they are not persisted in settings.
   */
  @Transient
  @NotNull
  public List<MantisFilter> getFilters() {
    return myFilters == null ? Collections.emptyList() : myFilters;
  }

  public void setFilters(@NotNull List<MantisFilter> filters) {
    myFilters = filters;
  }


  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MantisProject project = (MantisProject)o;

    if (myId != project.myId) return false;

    return true;
  }

  @Override
  public final int hashCode() {
    return myId;
  }

  @Override
  public String toString() {
    return getName();
  }
}
