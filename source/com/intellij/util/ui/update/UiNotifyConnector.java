/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

public class UiNotifyConnector implements Disposable, HierarchyListener{
  private final JComponent myComponent;
  private final MergingUpdateQueue myTarget;

  public UiNotifyConnector(final JComponent component, final MergingUpdateQueue target) {
    myComponent = component;
    myTarget = target;
    if (!component.isShowing()) {
      target.hideNotify();
    }
    component.addHierarchyListener(this);
  }

  public void hierarchyChanged(HierarchyEvent e) {
    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) > 0) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myComponent.isShowing()) {
            myTarget.showNotify();
          }
          else {
            myTarget.hideNotify();
          }
        }
      });
    }
  }

  public void dispose() {
    myTarget.hideNotify();
    myComponent.removeHierarchyListener(this);
  }

}
