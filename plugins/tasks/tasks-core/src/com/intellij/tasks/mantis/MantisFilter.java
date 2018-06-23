package com.intellij.tasks.mantis;

import com.intellij.tasks.mantis.model.FilterData;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

public final class MantisFilter {
  // Used for "[Last task] filter"
  public static final int UNSPECIFIED_FILTER_ID = 0;

  public static MantisFilter newUndefined() {
    return new MantisFilter(0, "-- all issues --");
  }

  private int myId;
  private String myName = "";

  @SuppressWarnings({"UnusedDeclaration"})
  public MantisFilter() {
  }

  public MantisFilter(int id, String name) {
    myId = id;
    myName = name;
  }

  public MantisFilter(@NotNull FilterData data) {
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
  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    this.myName = name;
  }

  public final boolean isUnspecified() {
    return getId() == UNSPECIFIED_FILTER_ID;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myId == ((MantisFilter)o).myId;
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
