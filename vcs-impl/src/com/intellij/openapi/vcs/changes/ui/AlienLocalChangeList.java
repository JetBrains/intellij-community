package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlienLocalChangeList extends LocalChangeList {
  private final List<Change> myChanges;
  private String myName;
  private String myComment;

  public AlienLocalChangeList(final List<Change> changes, final String name) {
    myChanges = changes;
    myName = name;
    myComment = "";
  }

  public Collection<Change> getChanges() {
    return myChanges;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull final String name) {
    myName = name;
  }

  public String getComment() {
    return myComment;
  }

  public void setComment(final String comment) {
    myComment = comment;
  }

  public boolean isDefault() {
    return false;
  }

  public boolean isInUpdate() {
    return false;
  }

  public boolean isReadOnly() {
    return false;
  }

  public void setReadOnly(final boolean isReadOnly) {
    throw new UnsupportedOperationException();
  }

  public LocalChangeList clone() {
    throw new UnsupportedOperationException();
  }

  public static final AlienLocalChangeList DEFAULT_ALIEN = new AlienLocalChangeList(Collections.<Change>emptyList(), "Default") {
    public boolean isDefault() {
      return true;
    }
  };
}
