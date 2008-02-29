package com.intellij.execution.executors;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.UIBundle;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class DefaultDebugExecutor extends Executor {
  @NonNls public static final String EXECUTOR_ID = ToolWindowId.DEBUG;
  private static final Icon TOOLWINDOW_ICON = IconLoader.getIcon("/general/toolWindowDebugger.png");
  private static final Icon ICON = IconLoader.getIcon("/actions/startDebugger.png");
  private static final Icon DISABLED_ICON = IconLoader.getIcon("/process/disabledDebug.png");
  private String myStartActionText = XDebuggerBundle.message("debugger.runner.start.action.text");
  private String myDescription = XDebuggerBundle.message("string.debugger.runner.description");

  public String getToolWindowId() {
    return ToolWindowId.DEBUG;
  }

  public Icon getToolWindowIcon() {
    return TOOLWINDOW_ICON;
  }

  @NotNull
  public Icon getIcon() {
    return ICON;
  }

  public Icon getDisabledIcon() {
    return DISABLED_ICON;
  }

  @NotNull
  public String getActionName() {
    return UIBundle.message("tool.window.name.debug");
  }

  @NotNull
  public String getId() {
    return EXECUTOR_ID;
  }

  public String getContextActionId() {
    return "DebugClass";
  }

  @NotNull
  public String getStartActionText() {
    return myStartActionText;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getHelpId() {
    return "debugging.DebugWindow";
  }

  public static Executor getDebugExecutorInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
  }
}
