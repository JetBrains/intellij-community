package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.project.Project;

public abstract class ToolWindowManagerEx extends ToolWindowManager{
  public static ToolWindowManagerEx getInstanceEx(final Project project){
    return (ToolWindowManagerEx)getInstance(project);
  }

  public abstract void addToolWindowManagerListener(ToolWindowManagerListener l);

  public abstract void removeToolWindowManagerListener(ToolWindowManagerListener l);

  /**
   * @return <code>ID</code> of tool window that was activated last time.
   */
  public abstract String getLastActiveToolWindowId();

  /**
   * @return layout of tool windows.
   */
  public abstract DesktopLayout getLayout();

  /**
   * Copied <code>layout</code> into internal layout and rearranges tool windows.
   */
  public abstract void setLayout(DesktopLayout layout);

  public abstract void clearSideStack();
}
