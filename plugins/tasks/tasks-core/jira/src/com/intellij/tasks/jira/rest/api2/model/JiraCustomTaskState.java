package com.intellij.tasks.jira.rest.api2.model;

import com.intellij.tasks.CustomTaskState;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JiraCustomTaskState implements CustomTaskState {
  private final int myTransitionId;
  private final String myPresentableName;
  private final int myResolutionId;

  public JiraCustomTaskState(int transitionId, @NotNull String presentableName, int resolutionId) {
    myTransitionId = transitionId;
    myPresentableName = presentableName;
    myResolutionId = resolutionId;
  }

  public boolean hasResolutionId() {
    return myResolutionId != 0;
  }

  public int getResolutionId() {
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
