/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class SurroundActionGroup extends ActionGroup {
  private final AnAction[] myChildren;

  public SurroundActionGroup() {
    myChildren = new AnAction[4];
    myChildren [0] = new SurroundAction(JPanel.class.getName());
    myChildren [1] = new SurroundAction(JScrollPane.class.getName());
    myChildren [2] = new SurroundAction(JSplitPane.class.getName());
    myChildren [3] = new SurroundAction(JTabbedPane.class.getName());
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren;
  }
}
