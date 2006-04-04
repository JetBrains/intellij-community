/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.openapi.components.ApplicationComponent;

import javax.swing.*;

import org.jetbrains.annotations.NonNls;

public class ToolTipHandlerProviderImpl extends ToolTipHandlerProvider implements ApplicationComponent {
  public void install(JComponent component) {
    if (component instanceof JTree) {
      TreeToolTipHandler.install((JTree)component);
    }
    else if (component instanceof JTable) {
      TableToolTipHandler.install((JTable)component);
    }
    else if (component instanceof JList) {
      ListToolTipHandler.install((JList)component);
    }
    else {
      ToolTipManager.sharedInstance().registerComponent(component);
    }
  }


  @NonNls
  public String getComponentName() {
    return "ToolTipHandlerProvider";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
