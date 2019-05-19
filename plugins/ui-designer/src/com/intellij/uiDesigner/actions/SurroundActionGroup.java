// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
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

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren;
  }
}
