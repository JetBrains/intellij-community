package com.intellij.tasks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class CustomTaskState {
  private String myId;
  private String myPresentableName;
  private boolean myPredefined;

  /**
   * For serialization purposes only.
   */
  public CustomTaskState() {
  }

  public CustomTaskState(@NotNull String id, @NotNull String name) {
    myId = id;
    myPresentableName = name;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  /**
   * For serialization purposes only.
   */
  public void setId(String id) {
    myId = id;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  /**
   * For serialization purposes only.
   */
  public void setPresentableName(@NotNull String name) {
    myPresentableName = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CustomTaskState)) return false;

    final CustomTaskState state = (CustomTaskState)o;

    return myId.equals(state.myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @NotNull
  public static CustomTaskState fromPredefined(@NotNull TaskState state) {
    final CustomTaskState result = new CustomTaskState(state.name(), state.getPresentableName());
    result.setPredefined(true);
    return result;
  }

  @Nullable
  public TaskState asPredefined() {
    if (isPredefined()) {
      try {
        return TaskState.valueOf(getId());
      }
      catch (IllegalArgumentException ignored) {
      }
    }
    return null;
  }

  private boolean isPredefined() {
    return myPredefined;
  }

  /**
   * For serialization purposes only.
   */
  public void setPredefined(boolean predefined) {
    myPredefined = predefined;
  }

  @Override
  public String toString() {
    return "CustomTaskState(id='" + myId + '\'' + ", name='" + myPresentableName + '\'' + ", myPredefined=" + myPredefined + ')';
  }
}
