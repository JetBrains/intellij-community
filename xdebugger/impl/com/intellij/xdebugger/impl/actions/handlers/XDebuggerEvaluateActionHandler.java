package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.evaluate.XExpressionEvaluationDialog;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebuggerEvaluateActionHandler extends XDebuggerSuspendedActionHandler {
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    XStackFrame stackFrame = session.getCurrentStackFrame();
    if (stackFrame == null) return;
    
    new XExpressionEvaluationDialog(session, editorsProvider, stackFrame).show();
  }

  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    if (!super.isEnabled(session, dataContext)) {
      return false;
    }

    XStackFrame stackFrame = session.getCurrentStackFrame();
    return stackFrame != null && stackFrame.getEvaluator() != null;
  }
}
