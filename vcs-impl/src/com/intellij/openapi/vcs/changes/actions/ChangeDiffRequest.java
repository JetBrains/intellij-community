package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;

/**
 * @author yole
 */
public class ChangeDiffRequest extends SimpleDiffRequest {
  private final Change[] myChanges;
  private final int myIndex;
  private final ShowDiffAction.DiffExtendUIFactory myActionsFactory;

  public ChangeDiffRequest(Project project, String windowtitle, final Change[] changes, final int index,
                           final ShowDiffAction.DiffExtendUIFactory actionsFactory) {
    super(project, windowtitle);
    myChanges = changes;
    myIndex = index;
    myActionsFactory = actionsFactory;
  }

  public Change[] getChanges() {
    return myChanges;
  }

  public int getIndex() {
    return myIndex;
  }

  public ShowDiffAction.DiffExtendUIFactory getActionsFactory() {
    return myActionsFactory;
  }
}