package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

/**
 * @author Vladimir Kondratyev
 */
public final class SplitHorizontalAction extends SplitAction{
  public SplitHorizontalAction() {
    super(SwingConstants.VERTICAL);
  }
}
