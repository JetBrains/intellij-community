package com.intellij.ui.navigation;

import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NotNull;

public abstract class Place implements ComparableObject {

  private Object[] myID;

  public Place(@NotNull final Object id) {
    this(new Object[] {id});
  }

  public Place(@NotNull final Object[] id) {
    myID = id;
  }

  public abstract void goThere();

  public final Object[] getEqualityObjects() {
    return myID;
  }

  public final boolean equals(final Object obj) {
    return ComparableObjectCheck.equals(this, obj);
  }

  public final int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }
}
