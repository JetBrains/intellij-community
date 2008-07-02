package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;

import javax.swing.*;
import java.util.List;

public class VcsCommittedViewAuxiliary {
  private final JPanel myPanel;
  private final List<AnAction> myPopupActions;
  private final Runnable myCalledOnViewDispose;

  public VcsCommittedViewAuxiliary(final JPanel panel, final List<AnAction> popupActions, final Runnable calledOnViewDispose) {
    myPanel = panel;
    myPopupActions = popupActions;
    myCalledOnViewDispose = calledOnViewDispose;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public List<AnAction> getPopupActions() {
    return myPopupActions;
  }

  public Runnable getCalledOnViewDispose() {
    return myCalledOnViewDispose;
  }
}
