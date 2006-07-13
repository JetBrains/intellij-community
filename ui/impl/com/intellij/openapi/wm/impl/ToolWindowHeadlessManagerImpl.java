/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 13-Jul-2006
 * Time: 12:07:39
 */
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@SuppressWarnings({"ConstantConditions"})
public class ToolWindowHeadlessManagerImpl extends ToolWindowManagerEx {
  private static final ToolWindow HEADLESS_WINDOW = new ToolWindow(){
    public boolean isActive() {
      return false;
    }

    public void activate(@Nullable Runnable runnable) {
    }

    public boolean isVisible() {
      return false;
    }

    public void show(@Nullable Runnable runnable) {
    }

    public void hide(@Nullable Runnable runnable) {
    }

    public ToolWindowAnchor getAnchor() {
      return ToolWindowAnchor.BOTTOM;
    }

    public void setAnchor(ToolWindowAnchor anchor, @Nullable Runnable runnable) {
    }

    public boolean isAutoHide() {
      return false;
    }

    public void setAutoHide(boolean state) {
    }

    public ToolWindowType getType() {
      return ToolWindowType.SLIDING;
    }

    public void setType(ToolWindowType type, @Nullable Runnable runnable) {
    }

    public Icon getIcon() {
      return null;
    }

    public void setIcon(Icon icon) {
    }

    public String getTitle() {
      return "";
    }

    public void setTitle(String title) {
    }

    public boolean isAvailable() {
      return false;
    }

    public void setAvailable(boolean available, @Nullable Runnable runnable) {
    }

    public void installWatcher(ContentManager contentManager) {
    }

    public JComponent getComponent() {
      return null;
    }
  };
  public ToolWindow registerToolWindow(String id, JComponent component, ToolWindowAnchor anchor) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(String id, JComponent component, ToolWindowAnchor anchor, Disposable parentDisposable) {
    return HEADLESS_WINDOW;
  }

  public void unregisterToolWindow(String id) {
  }

  public void activateEditorComponent() {
  }

  public boolean isEditorComponentActive() {
    return false;
  }

  public String[] getToolWindowIds() {
    return new String[0];
  }

  public String getActiveToolWindowId() {
    return null;
  }

  public ToolWindow getToolWindow(String id) {
    return HEADLESS_WINDOW;
  }

  public void invokeLater(Runnable runnable) {
  }

  public void addToolWindowManagerListener(ToolWindowManagerListener l) {

  }

  public void removeToolWindowManagerListener(ToolWindowManagerListener l) {
  }

  public String getLastActiveToolWindowId() {
    return null;
  }

  public String getLastActiveToolWindowId(Condition<JComponent> condition) {
    return null;
  }

  public DesktopLayout getLayout() {
    return new DesktopLayout();
  }

  public void setLayoutToRestoreLater(DesktopLayout layout) {
  }

  public DesktopLayout getLayoutToRestoreLater() {
    return new DesktopLayout();
  }

  public void setLayout(DesktopLayout layout) {
  }

  public void clearSideStack() {
  }
}