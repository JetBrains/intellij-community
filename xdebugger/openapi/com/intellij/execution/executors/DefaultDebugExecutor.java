package com.intellij.execution.executors;

import com.intellij.execution.Executor;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.UIBundle;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author spleaner
 */
public class DefaultDebugExecutor extends Executor {
  @NonNls public static final String EXECUTOR_ID = ToolWindowId.DEBUG;

  public static RunnerInfo DEFAULT_DEBUGGER_INFO = new RunnerInfo(ToolWindowId.DEBUG,
                                                                 XDebuggerBundle.message("string.debugger.runner.description"), IconLoader.getIcon("/actions/startDebugger.png"),
                                                                 IconLoader.getIcon("/general/toolWindowDebugger.png"), ToolWindowId.DEBUG, "debugging.debugWindow") {

    @Override
    public Icon getEnabledIcon() {
      return getToolWindowIcon();
    }

    @Override
    public Icon getDisabledIcon() {
      return IconLoader.getIcon("/process/disabledDebug.png");
    }
  };


  public DefaultDebugExecutor() {
    super(IconLoader.getIcon("/actions/startDebugger.png"), EXECUTOR_ID, UIBundle.message("tool.window.name.debug"), "DebugClass");
  }

  public String getStartActionText() {
    return XDebuggerBundle.message("debugger.runner.start.action.text");
  }
}
