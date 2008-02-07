package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.evaluate.XExpressionEvaluationDialog;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebuggerEvaluateActionHandler extends XDebuggerSuspendedActionHandler {
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    XSuspendContext suspendContext = session.getSuspendContext();
    XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    new XExpressionEvaluationDialog(session.getProject(), editorsProvider, suspendContext, session.getCurrentPosition()).show();
  }

  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    return super.isEnabled(session, dataContext) && session.getSuspendContext().getEvaluator() != null;
  }
}
