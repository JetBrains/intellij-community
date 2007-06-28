package com.intellij.ui.navigation;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NotNull;

public abstract class Place<T> implements ComparableObject {

  private Object[] myID;
  private Presentation myPresentation;

  private T myObject;

  public Place(@NotNull final Object id) {
    this(new Object[] {id});
  }

  public Place(@NotNull final Object[] id) {
    myID = id;
  }

  public Place<T> setObject(final T object) {
    myObject = object;
    return this;
  }

  public T getObject() {
    return myObject;
  }

  public abstract void goThere();

  public Place setPresentation(final Presentation presentation) {
    myPresentation = presentation;
    return this;
  }

  public final Object[] getEqualityObjects() {
    return myID;
  }

  public final boolean equals(final Object obj) {
    return ComparableObjectCheck.equals(this, obj);
  }

  public final int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

  public Presentation getPresentation() {
    return myPresentation;
  }
}
