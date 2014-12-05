package com.intellij.tasks.jira.rest.model;

import com.intellij.tasks.CustomTaskState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JiraCustomTaskState implements CustomTaskState {
  private final int myTransitionId;
  private final String myPresentableName;
  private final String myResolutionId;

  public JiraCustomTaskState(@NotNull String presentableName, int transitionId, @Nullable String resolutionName) {
    myTransitionId = transitionId;
    myPresentableName = presentableName;
    myResolutionId = resolutionName;
  }

  @Nullable
  public String getResolutionName() {
    return myResolutionId;
  }

  @NotNull
  @Override
  public String getId() {
    return String.valueOf(myTransitionId);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return myPresentableName;
  }
}
