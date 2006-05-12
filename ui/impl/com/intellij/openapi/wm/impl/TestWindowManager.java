package com.intellij.openapi.wm.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class TestWindowManager extends WindowManagerEx implements ApplicationComponent{
  private static final StatusBar ourStatusBar = new DummyStatusBar();
  
  public final void doNotSuggestAsParent(final Window window) {
  }

  public final Window suggestParentWindow(final Project project) {
    return null;
  }

  public final StatusBar getStatusBar(final Project project) {
    return ourStatusBar;
  }

  private static final class DummyStatusBar implements StatusBarEx {
    public final void setInfo(final String s) {}

    public void fireNotificationPopup(JComponent content, final Color backgroundColor) {
    }

    public final String getInfo() {
      return null;
    }

    public final void setPosition(final String s) {}

    public final void setStatus(final String s) {}

    public final void setStatusEnabled(final boolean enabled) {}

    public final void showCancelButton(final Icon icon, final ActionListener listener, final String tooltopText) {}

    public final void hideCancelButton() {}

    public final void addProgress() {}

    public final void setProgressValue(final int progress) {}

    public final void hideProgress() {}

    public final void setWriteStatus(final boolean locked) {}

    public final void clear() {}

    public void addCustomIndicationComponent(JComponent c) {}

    public final void updateEditorHighlightingStatus(final boolean isClear) {}

    public void cleanupCustomComponents() {
    }
  }

  public IdeFrame[] getAllFrames() {
    return new IdeFrame[0];
  }

  public final IdeFrame getFrame(final Project project) {
    return null;
  }

  public final IdeFrame allocateFrame(final Project project) {
    throw new UnsupportedOperationException();
  }

  public final void releaseFrame(final IdeFrame frame) {
    throw new UnsupportedOperationException();
  }

  public final Component getFocusedComponent(final Window window) {
    throw new UnsupportedOperationException();
  }

  public final Component getFocusedComponent(final Project project) {
    throw new UnsupportedOperationException();
  }

  public final Window getMostRecentFocusedWindow() {
    return null;
  }

  public final CommandProcessor getCommandProcessor() {
    throw new UnsupportedOperationException();
  }

  public final DesktopLayout getLayout() {
    throw new UnsupportedOperationException();
  }

  public final void setLayout(final DesktopLayout layout) {
    throw new UnsupportedOperationException();
  }

  public final void dispatchComponentEvent(final ComponentEvent e) {
    throw new UnsupportedOperationException();
  }

  public final Rectangle getScreenBounds() {
    throw new UnsupportedOperationException();
  }

  public final boolean isInsideScreenBounds(final int x, final int y, final int width) {
    throw new UnsupportedOperationException();
  }

  public final boolean isInsideScreenBounds(final int x, final int y) {
    throw new UnsupportedOperationException();
  }

  public final boolean isAlphaModeSupported() {
    throw new UnsupportedOperationException();
  }

  public final void setAlphaModeRatio(final Window window, final float ratio) {
    throw new UnsupportedOperationException();
  }

  public final boolean isAlphaModeEnabled(final Window window) {
    throw new UnsupportedOperationException();
  }

  public final void setAlphaModeEnabled(final Window window, final boolean state) {
    throw new UnsupportedOperationException();
  }

  public void hideDialog(JDialog dialog, Project project) {
    dialog.dispose();
  }

  public final String getComponentName() {
    return "TestWindowManager";
  }

  public final void initComponent() { }

  public final void disposeComponent() {
  }
}
