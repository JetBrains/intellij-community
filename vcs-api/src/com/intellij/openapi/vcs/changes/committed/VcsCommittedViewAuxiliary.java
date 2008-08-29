package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;

import java.util.List;

public class VcsCommittedViewAuxiliary {
  //private final JPanel myPanel;
  private final List<AnAction> myToolbarActions;
  private final List<AnAction> myPopupActions;
  private final Runnable myCalledOnViewDispose;

  public VcsCommittedViewAuxiliary(final List<AnAction> popupActions, final Runnable calledOnViewDispose,
                                   final List<AnAction> toolbarActions) {
    myToolbarActions = toolbarActions;
    myPopupActions = popupActions;
    myCalledOnViewDispose = calledOnViewDispose;
  }

  public List<AnAction> getPopupActions() {
    return myPopupActions;
  }

  public Runnable getCalledOnViewDispose() {
    return myCalledOnViewDispose;
  }

  public List<AnAction> getToolbarActions() {
    return myToolbarActions;
  }
}
