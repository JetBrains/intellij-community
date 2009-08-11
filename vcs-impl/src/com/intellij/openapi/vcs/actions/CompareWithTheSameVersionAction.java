package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;

public class CompareWithTheSameVersionAction extends AbstractShowDiffAction{
  @Override
  protected ProjectLevelVcsManagerImpl.MyBackgroundableActions getKey() {
    return ProjectLevelVcsManagerImpl.MyBackgroundableActions.COMPARE_WITH_SAME;
  }
}
