package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.actions.ChooseDebugConfigurationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ResumeAction extends XDebuggerActionBase {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (!performWithHandler(e)) {
      new ChooseDebugConfigurationAction().actionPerformed(e);
    }
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getResumeActionHandler();
  }
}
