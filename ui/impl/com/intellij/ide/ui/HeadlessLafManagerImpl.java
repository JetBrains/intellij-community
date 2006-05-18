/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: anna
 * Date: 17-May-2006
 */
public class HeadlessLafManagerImpl extends LafManager implements ApplicationComponent {
  public UIManager.LookAndFeelInfo[] getInstalledLookAndFeels() {
    return new UIManager.LookAndFeelInfo[0];
  }

  public UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
    return null;
  }

  public boolean isUnderAquaLookAndFeel() {
    return false;
  }

  public boolean isUnderQuaquaLookAndFeel() {
    return false;
  }

  public void setCurrentLookAndFeel(UIManager.LookAndFeelInfo lookAndFeelInfo) {
  }

  public void updateUI() {
  }

  public void repaintUI() {
  }

  public void addLafManagerListener(LafManagerListener l) {
  }

  public void removeLafManagerListener(LafManagerListener l) {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "HeadlessLafManagerImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
