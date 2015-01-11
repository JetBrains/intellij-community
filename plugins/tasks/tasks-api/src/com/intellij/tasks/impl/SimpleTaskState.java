package com.intellij.tasks.impl;

import com.intellij.tasks.CustomTaskState;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class SimpleTaskState implements CustomTaskState {
  private final String myId;
  private final String myPresentableName;

  public SimpleTaskState(@NotNull String id, @NotNull String presentableName) {
    myId = id;
    myPresentableName = presentableName;
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return myPresentableName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleTaskState)) return false;

    final SimpleTaskState state = (SimpleTaskState)o;

    if (!myId.equals(state.myId)) return false;
    if (!myPresentableName.equals(state.myPresentableName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myId.hashCode();
    result = 31 * result + myPresentableName.hashCode();
    return result;
  }
}
