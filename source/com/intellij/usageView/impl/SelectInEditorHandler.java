/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usageView.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author dyoma
 */
public class SelectInEditorHandler {
  public static void installKeyListener(final JComponent component, final Project project) {
    component.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) selectInEditor(component, project);
      }
    });
  }

  public static void selectInEditor(final JComponent component, final Project project) {
    final Navigatable navigatable = (Navigatable)DataManager.getInstance().getDataContext(component).getData(DataConstants.NAVIGATABLE);

    if (navigatable != null) {
      navigatable.navigate(false);
    }
  }

}
